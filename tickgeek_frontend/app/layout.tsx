import type { Metadata } from "next";
import "./globals.css";
import BackendWakeUp from "@/components/BackendWakeUp";

export const metadata: Metadata = {
  title: "TickGeek",
  description: "Sell tickets for your events",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>
        <BackendWakeUp />
        {children}
      </body>
    </html>
  );
}
