# NeoSerf Firestore Security Specification

## 1. Data Invariants
- All user data is strictly isolated under `/users/{userId}/{subcollection}/{docId}`.
- Every operation (`get`, `list`, `create`, `update`, `delete`) requires an authenticated Google Sign-In session (`request.auth != null`) where `request.auth.uid == userId` AND `resource.data.userId == request.auth.uid` (or `request.resource.data.userId == request.auth.uid` on create).
- Every `create` and `update` operation enforces strict field allowlisting (`keys().hasOnly(...)`), required keys (`keys().hasAll(...)`), string length limits, and server timestamp upper bounds (`<= request.time`).
- `userId` and `createdAt` are immutable across all updates (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read**: `GET /users/alice/bookmarks/b1` with `auth = null` -> Reject (`isSignedIn()` fails).
2. **Cross-User Read**: `GET /users/bob/bookmarks/b1` as `alice` -> Reject (`request.auth.uid == userId` fails).
3. **Cross-User List Scraping**: `LIST /users/bob/passwords` as `alice` -> Reject (`request.auth.uid == userId && resource.data.userId == request.auth.uid` fails).
4. **Identity Spoofing on Create**: `POST /users/alice/bookmarks/b1` with `{"userId": "bob", ...}` as `alice` -> Reject (`incoming().userId == request.auth.uid` fails).
5. **Shadow Update / Ghost Field Injection**: `PATCH /users/alice/bookmarks/b1` with `{"isAdmin": true}` -> Reject (`hasOnly` and `affectedKeys().hasOnly(...)` fail).
6. **Ownership Transfer on Update**: `PATCH /users/alice/passwords/p1` changing `userId` to `"bob"` -> Reject (`incoming().userId == existing().userId` fails).
7. **CreatedAt Tampering on Update**: `PATCH /users/alice/bookmarks/b1` mutating `createdAt` -> Reject (`incoming().createdAt == existing().createdAt` fails).
8. **Future Timestamp Spoofing**: `POST /users/alice/history/h1` with `createdAt = request.time + 1h` -> Reject (`createdAt <= request.time` fails).
9. **Oversized String Denial-of-Wallet**: `POST /users/alice/bookmarks/b1` with 10KB `title` -> Reject (`title.size() <= 200` fails).
10. **Type Confusion**: `PATCH /users/alice/speed_dial/s1` with `position: "first"` instead of `int` -> Reject (`isValidSpeedDialTile` fails).
11. **ID Poisoning**: `POST /users/alice/bookmarks/invalid$id!` -> Reject (`isValidId` regex fails).
12. **Unprotected Root Path Write**: `POST /unprotected_collection/doc1` -> Reject (global default-deny catch-all).
