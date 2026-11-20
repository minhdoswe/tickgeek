import EmailForm from "@/components/auth/EmailForm";

export default function SignInPage() {
  return (
    <main>
      <h1>Sign in or create an account</h1>
      <p className="muted">Enter your email and we&apos;ll send you a 6-digit code. No password needed.</p>
      <EmailForm />
    </main>
  );
}
