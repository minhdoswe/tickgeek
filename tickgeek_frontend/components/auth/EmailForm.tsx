"use client";

import { useActionState, useState, type SubmitEvent } from "react";
import { requestCodeAction } from "@/action/authAction";
import type { FormState } from "@/action/formState";
import FieldError from "@/components/auth/FieldError";

const INITIAL_STATE: FormState = {};
const EMAIL_MAX_LENGTH = 254;
const MISSING_MESSAGE = "Enter your email address.";
const INVALID_MESSAGE = "Enter a valid email address.";

/**
 * Browser-side check before sending. The backend check stays the source of truth
 * (specs/001-request-code/research.md R11).
 */
function emailProblem(input: HTMLInputElement): string | undefined {
  if (input.validity.valueMissing) {
    return MISSING_MESSAGE;
  }
  if (input.validity.typeMismatch) {
    return INVALID_MESSAGE;
  }
  return undefined;
}

export default function EmailForm() {
  const [state, formAction, pending] = useActionState(requestCodeAction, INITIAL_STATE);
  const [localError, setLocalError] = useState<string>();

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    const input = event.currentTarget.elements.namedItem("email");
    const problem = input instanceof HTMLInputElement ? emailProblem(input) : undefined;
    if (problem) {
      event.preventDefault();
    }
    setLocalError(problem);
  }

  return (
    <form action={formAction} onSubmit={handleSubmit} noValidate>
      <label>
        Email address
        <input
          type="email"
          name="email"
          autoComplete="email"
          required
          maxLength={EMAIL_MAX_LENGTH}
          aria-invalid={localError ? true : undefined}
          aria-describedby="email-error"
          onChange={() => setLocalError(undefined)}
        />
      </label>
      {localError ? (
        <p className="error" id="email-error">
          {localError}
        </p>
      ) : (
        <FieldError fields={state.fields} name="email" />
      )}
      {state.error && !state.fields && <p className="error">{state.error.message}</p>}
      {state.notice && (
        <p className="muted" role="status">
          {state.notice}
        </p>
      )}
      <button type="submit" disabled={pending}>
        {pending ? "Sending…" : "Continue"}
      </button>
    </form>
  );
}
