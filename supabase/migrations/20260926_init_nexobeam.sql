-- ====================================================================
-- NexoBeam Messenger: Initial PostgreSQL Schema & RLS Setup for Supabase
-- ====================================================================

-- 1. Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 2. Profiles Table (Linked to auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    bio TEXT,
    last_seen TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT username_length CHECK (char_length(username) >= 3 AND char_length(username) <= 30),
    CONSTRAINT username_chars CHECK (username ~* '^[a-zA-Z0-9_]+$')
);

CREATE INDEX IF NOT EXISTS idx_profiles_username_trgm ON public.profiles USING gin (username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_profiles_display_name ON public.profiles (display_name);

-- 3. Chats Table
DO $$ BEGIN
    CREATE TYPE chat_type AS ENUM ('direct', 'group');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS public.chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type chat_type NOT NULL DEFAULT 'direct',
    title TEXT,
    avatar_url TEXT,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    direct_key TEXT UNIQUE,
    last_message_text TEXT,
    last_message_at TIMESTAMPTZ,
    last_message_sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chats_last_message_at ON public.chats (last_message_at DESC NULLS LAST);

-- 4. Chat Members Table
DO $$ BEGIN
    CREATE TYPE member_role AS ENUM ('member', 'admin', 'owner');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS public.chat_members (
    chat_id UUID NOT NULL REFERENCES public.chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role member_role NOT NULL DEFAULT 'member',
    unread_count INT NOT NULL DEFAULT 0,
    joined_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    last_read_message_id UUID,
    PRIMARY KEY (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_members_user ON public.chat_members (user_id);

-- 5. Messages Table
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_id UUID NOT NULL REFERENCES public.chats(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE SET NULL,
    client_message_id TEXT,
    text TEXT,
    reply_to_message_id UUID REFERENCES public.messages(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN DEFAULT false NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_chat_id_created_at ON public.messages (chat_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_client_id ON public.messages (chat_id, client_message_id) WHERE client_message_id IS NOT NULL;

-- 6. Attachments Table
DO $$ BEGIN
    CREATE TYPE attachment_type AS ENUM ('image', 'video', 'audio', 'document', 'other');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS public.attachments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
    chat_id UUID NOT NULL REFERENCES public.chats(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type TEXT NOT NULL,
    type attachment_type NOT NULL,
    width INT,
    height INT,
    duration_ms INT,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_attachments_message ON public.attachments (message_id);

-- 7. User Devices Table (Push token storage)
CREATE TABLE IF NOT EXISTS public.user_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL,
    push_provider TEXT NOT NULL DEFAULT 'fcm',
    push_token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    last_seen TIMESTAMPTZ DEFAULT now() NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    UNIQUE (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_user ON public.user_devices (user_id);

-- 8. Functions & Triggers
-- A. Auto profile generation from Auth User
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.profiles (id, username, display_name, avatar_url)
  VALUES (
    new.id,
    COALESCE(new.raw_user_meta_data->>'username', 'user_' || SUBSTRING(new.id::text, 1, 8)),
    COALESCE(new.raw_user_meta_data->>'display_name', 'User'),
    new.raw_user_meta_data->>'avatar_url'
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- B. Handle new message denormalization & unread count
CREATE OR REPLACE FUNCTION public.handle_new_message()
RETURNS trigger AS $$
BEGIN
  UPDATE public.chats
  SET 
    last_message_text = CASE WHEN new.text IS NOT NULL AND length(new.text) > 0 THEN new.text ELSE 'Attachment' END,
    last_message_at = new.created_at,
    last_message_sender_id = new.sender_id,
    updated_at = now()
  WHERE id = new.chat_id;
  
  UPDATE public.chat_members
  SET unread_count = unread_count + 1
  WHERE chat_id = new.chat_id AND user_id != new.sender_id;

  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_message_created ON public.messages;
CREATE TRIGGER on_message_created
  AFTER INSERT ON public.messages
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_message();

-- 9. Row Level Security Policies (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chats ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_devices ENABLE ROW LEVEL SECURITY;

-- Profiles Policies
DROP POLICY IF EXISTS "Profiles readable by authenticated users" ON public.profiles;
CREATE POLICY "Profiles readable by authenticated users"
ON public.profiles FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile"
ON public.profiles FOR UPDATE TO authenticated USING (auth.uid() = id);

-- Chats Policies
DROP POLICY IF EXISTS "Users can see chats they belong to" ON public.chats;
CREATE POLICY "Users can see chats they belong to"
ON public.chats FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.chat_members
    WHERE chat_members.chat_id = chats.id
      AND chat_members.user_id = auth.uid()
  )
);

DROP POLICY IF EXISTS "Users can insert chats" ON public.chats;
CREATE POLICY "Users can insert chats"
ON public.chats FOR INSERT TO authenticated
WITH CHECK (auth.uid() = created_by);

-- Chat Members Policies
DROP POLICY IF EXISTS "Members visible to fellow chat participants" ON public.chat_members;
CREATE POLICY "Members visible to fellow chat participants"
ON public.chat_members FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.chat_members AS cm
    WHERE cm.chat_id = chat_members.chat_id
      AND cm.user_id = auth.uid()
  )
);

DROP POLICY IF EXISTS "Chat creator can add members" ON public.chat_members;
CREATE POLICY "Chat creator can add members"
ON public.chat_members FOR INSERT TO authenticated
WITH CHECK (
  auth.uid() = user_id OR
  EXISTS (
    SELECT 1 FROM public.chats
    WHERE chats.id = chat_members.chat_id AND chats.created_by = auth.uid()
  )
);

-- Messages Policies
DROP POLICY IF EXISTS "Messages readable by chat members" ON public.messages;
CREATE POLICY "Messages readable by chat members"
ON public.messages FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.chat_members
    WHERE chat_members.chat_id = messages.chat_id
      AND chat_members.user_id = auth.uid()
  )
);

DROP POLICY IF EXISTS "Members can send messages" ON public.messages;
CREATE POLICY "Members can send messages"
ON public.messages FOR INSERT TO authenticated
WITH CHECK (
  auth.uid() = sender_id AND
  EXISTS (
    SELECT 1 FROM public.chat_members
    WHERE chat_members.chat_id = messages.chat_id
      AND chat_members.user_id = auth.uid()
  )
);

DROP POLICY IF EXISTS "Sender can update own message" ON public.messages;
CREATE POLICY "Sender can update own message"
ON public.messages FOR UPDATE TO authenticated
USING (auth.uid() = sender_id);

-- Attachments Policies
DROP POLICY IF EXISTS "Attachments readable by chat members" ON public.attachments;
CREATE POLICY "Attachments readable by chat members"
ON public.attachments FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.chat_members
    WHERE chat_members.chat_id = attachments.chat_id
      AND chat_members.user_id = auth.uid()
  )
);

DROP POLICY IF EXISTS "Sender can insert attachments" ON public.attachments;
CREATE POLICY "Sender can insert attachments"
ON public.attachments FOR INSERT TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.chat_members
    WHERE chat_members.chat_id = attachments.chat_id
      AND chat_members.user_id = auth.uid()
  )
);

-- User Devices Policies
DROP POLICY IF EXISTS "Users control own devices" ON public.user_devices;
CREATE POLICY "Users control own devices"
ON public.user_devices FOR ALL TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- 10. Enable Supabase Realtime publication
ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
ALTER PUBLICATION supabase_realtime ADD TABLE public.chats;
