CREATE TABLE public.ai_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    conversation_id UUID NOT NULL
        REFERENCES public.ai_conversations(id)
        ON DELETE CASCADE,

    role TEXT NOT NULL
        CHECK (role IN ('user', 'assistant')),

    content TEXT NOT NULL
        CHECK (length(trim(content)) > 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_messages_conversation_date
ON public.ai_messages (conversation_id, created_at, id);

ALTER TABLE public.ai_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own AI messages"
ON public.ai_messages
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.ai_conversations c
        WHERE c.id = conversation_id
          AND c.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can insert own AI messages"
ON public.ai_messages
FOR INSERT
TO authenticated
WITH CHECK (
    role = 'user'
    AND EXISTS (
        SELECT 1
        FROM public.ai_conversations c
        WHERE c.id = conversation_id
          AND c.user_id = (SELECT auth.uid())
    )
);

CREATE POLICY "Users can delete own AI messages"
ON public.ai_messages
FOR DELETE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.ai_conversations c
        WHERE c.id = conversation_id
          AND c.user_id = (SELECT auth.uid())
    )
);