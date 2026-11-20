import type { FieldErrors } from "@/lib/api/types";

type Props = { fields?: FieldErrors; name: string };

export default function FieldError({ fields, name }: Props) {
  const messages = fields?.[name];
  if (!messages?.length) {
    return null;
  }
  return (
    <p className="error" id={`${name}-error`}>
      {messages.join(" ")}
    </p>
  );
}
