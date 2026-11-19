# Spec Brief: Email Code Sign-In

The 20% of [spec.md](spec.md) you need. It covers **what** we build, not how.
IDs (`FR-xxx`, `SC-xxx`) lead to the full text.

> **Before you read:** write 3 lines on how *you* think sign-in by email code should work.
> Compare after. Where you differ is the part worth learning.

## 1. One breath

The user types an email and gets a 6-digit code. Right code: an existing user is signed in. A
new user types a name, and then the account is made. No passwords. One screen for sign-in and
sign-up.

## 2. User flow

1. Enter email (one screen for everyone). `FR-001`
2. Get a code by email; it is valid 10 min. `FR-003`, `FR-005`
3. Enter code:
   - **Known email**: signed in. `FR-010`
   - **New email**: name step (first + last, 1–50 chars). `FR-018`
4. Land on the page that sent you, or the dashboard. `FR-022`
5. Stay signed in 30 days from the last signed-in action, per device. `FR-013`, `FR-014`

Side paths: resend after 60 s, change email. `FR-012`

## 3. Rules (MUST)

| ID | Rule | Why |
|---|---|---|
| FR-004, SC-005 | Before the code check, a known email and a new email look the same: screens, errors, timing. | Hides who has an account. |
| FR-005 | A code works once and dies after 10 min. | Stops replay. |
| FR-006 | A new code kills all older ones. | Only the latest email works. |
| FR-007, SC-006 | Max 5 wrong tries per code. | 6 digits are easy to guess. |
| FR-008 | 60 s between requests; max 5 codes/hour/email. | Stops spam. |
| FR-020 | Also limit per source and system-wide; refusals give a generic message. | Stops mass abuse. |
| FR-011 | No account until the code is verified **and** the name is entered. | No half-made accounts. |
| FR-019 | Only the browser that verified the code may finish the name step; it works once. | Nobody else can claim the email. |
| FR-021, SC-004 | Two name submits at once give one account, and both are signed in. | No duplicates. |
| FR-013 | Sign-out ends only this device. | Multi-device. |
| FR-022 | Redirect only to a TickGeek page. | No open redirect. |

## 4. Out of scope

Passwords, Google/Apple login, magic links, MFA, editing the name, changing the email, and
languages other than English (`FR-024`). Guests buying tickets don't need accounts.

## 5. Decisions made: check you agree

| Decision | Value | Change if… |
|---|---|---|
| Code life / tries | 10 min / 5 | users complain about slow email |
| Resend cooldown / hourly cap | 60 s / 5 | real users hit it |
| Session idle timeout | 30 days | you need stricter security |
| Sign-out | this device only | you want "sign out everywhere" |
| Name | required, 1–50 chars | you need a display name instead |

## 6. Quiz (answer before opening the answers)

1. Bob types Alice's email. What does Bob learn?
2. The user opens two tabs, requests a code in each, and types the first code. Does it work?
3. A new user closes the tab on the name step and comes back 5 min later. What happens? After 20 min?
4. The email provider fails. What does the user see?

<details><summary>Answers</summary>

1. Nothing. He sees the same screen as for any email. (`FR-004`)
2. No. The second request killed it. (`FR-006`)
3. 5 min: the name step resumes, same browser. 20 min: start over. (`FR-019`)
4. The normal "code sent" screen. They can resend after 60 s. (Edge cases, `FR-008`)

</details>
