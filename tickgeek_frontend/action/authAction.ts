"use server";

import { requestCode } from "@/lib/api/auth";
import { formText, toFormState, type FormState } from "@/action/formState";

export async function requestCodeAction(_state: FormState, formData: FormData): Promise<FormState> {
  try {
    const sent = await requestCode(formText(formData, "email"));
    return { notice: `We sent a 6-digit code to ${sent.email}. It is valid for ${sent.codeValidMinutes} minutes.` };
  } catch (error) {
    return toFormState(error);
  }
}
