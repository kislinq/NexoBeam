-- Break recursive RLS evaluation when policies inspect chat_members.
CREATE OR REPLACE FUNCTION public.is_chat_member(target_chat_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.chat_members AS member
    WHERE member.chat_id = target_chat_id
      AND member.user_id = auth.uid()
  );
$$;

REVOKE ALL ON FUNCTION public.is_chat_member(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_chat_member(uuid) TO authenticated;

DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
CREATE POLICY "Users can insert own profile"
ON public.profiles FOR INSERT TO authenticated
WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can see chats they belong to" ON public.chats;
CREATE POLICY "Users can see chats they belong to"
ON public.chats FOR SELECT TO authenticated
USING (public.is_chat_member(id) OR created_by = auth.uid());

DROP POLICY IF EXISTS "Members visible to fellow chat participants" ON public.chat_members;
CREATE POLICY "Members visible to fellow chat participants"
ON public.chat_members FOR SELECT TO authenticated
USING (public.is_chat_member(chat_id));

DROP POLICY IF EXISTS "Messages readable by chat members" ON public.messages;
CREATE POLICY "Messages readable by chat members"
ON public.messages FOR SELECT TO authenticated
USING (public.is_chat_member(chat_id));

DROP POLICY IF EXISTS "Members can send messages" ON public.messages;
CREATE POLICY "Members can send messages"
ON public.messages FOR INSERT TO authenticated
WITH CHECK (auth.uid() = sender_id AND public.is_chat_member(chat_id));

DROP POLICY IF EXISTS "Attachments readable by chat members" ON public.attachments;
CREATE POLICY "Attachments readable by chat members"
ON public.attachments FOR SELECT TO authenticated
USING (public.is_chat_member(chat_id));

DROP POLICY IF EXISTS "Sender can insert attachments" ON public.attachments;
CREATE POLICY "Sender can insert attachments"
ON public.attachments FOR INSERT TO authenticated
WITH CHECK (public.is_chat_member(chat_id));

DROP POLICY IF EXISTS "Users can read own avatar objects" ON storage.objects;
CREATE POLICY "Users can read own avatar objects"
ON storage.objects FOR SELECT TO authenticated
USING (
  bucket_id = 'avatars'
  AND (storage.foldername(name))[1] = auth.uid()::text
);

DROP POLICY IF EXISTS "Users can upload own avatar objects" ON storage.objects;
CREATE POLICY "Users can upload own avatar objects"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
  bucket_id = 'avatars'
  AND (storage.foldername(name))[1] = auth.uid()::text
);

DROP POLICY IF EXISTS "Users can update own avatar objects" ON storage.objects;
CREATE POLICY "Users can update own avatar objects"
ON storage.objects FOR UPDATE TO authenticated
USING (
  bucket_id = 'avatars'
  AND (storage.foldername(name))[1] = auth.uid()::text
)
WITH CHECK (
  bucket_id = 'avatars'
  AND (storage.foldername(name))[1] = auth.uid()::text
);
