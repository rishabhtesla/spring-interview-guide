# Lesson 2: custom authentication without custom cryptography

[Module home](../readme.md) | [Next: database and APIs](03-database-and-api.md)

## What "custom auth service" means here

We implement registration and login ourselves using Spring Security's BCrypt
and JWT libraries. We do **not** invent hashing, signatures or a token parser.
This is a credentials-to-token teaching service, not a standards-compliant
OAuth2 authorization server or OpenID Connect provider.

Read [AuthController](../auth-service/src/main/java/com/example/interview/auth/AuthController.java)
and [AuthSecurity](../auth-service/src/main/java/com/example/interview/auth/AuthSecurity.java).

## Registration flow

1. JSON binds to `Credentials`; validation checks email and password length.
2. Email is normalized to lowercase (an explicit account-ID policy for this lab).
3. BCrypt creates a salted adaptive password hash. The raw password is not stored.
4. PostgreSQL enforces email uniqueness using a constraint and an atomic
   `ON CONFLICT` insertion.
5. The API returns the new account ID/email, never its password/hash.

BCrypt's cost is set to 12 in the application. Increasing cost makes offline
guessing more expensive but also costs login CPU. Benchmark rather than
choosing an enormous number. BCrypt has a 72-byte input limit, so we also
check UTF-8 byte length; 40 multi-byte characters can exceed that limit.

The service accepts 12-72 characters subject to that byte limit. This is a
simple lab policy, not a complete password-risk policy.

## Login flow

Look up the normalized email, compare the submitted password to the stored
hash, and return generic 401 invalid credentials on failure. Unknown accounts
still perform a comparison against a dummy hash to reduce a trivial timing
difference. This does not eliminate all account-enumeration channels:
registration returns 409 on duplicate email, for example.

After success, the auth service issues an RS256-signed JWT valid for 900 seconds.

| Claim | Meaning |
| --- | --- |
| `sub` | Stable account UUID; used for ownership |
| `iss` | `interview-lab`, the expected issuer |
| `aud` | `interview-api`, the intended API audience |
| `iat`, `exp` | Issued-at and expiration timestamps |
| `jti` | Token ID |
| `scope` | `orders notifications`, fixed capabilities for registered users |

Clients cannot choose scopes, subject or expiry in the login JSON.
There is no role-elevation endpoint.

## Signed does not mean encrypted

A JWT's header and payload are Base64URL-encoded and readable. Do not put
passwords, secrets or unnecessary private data in it. The signature proves
integrity/authenticity when verified with the trusted key; it does not hide
claims.

Only auth has the RSA private key. Gateway and resource services receive
the public key. This is preferable to giving every service a shared HMAC
signing secret: a verifier with only a public key cannot mint new tokens.
All processes must use the same trusted issuer and public key for the lab.

Read [JwtSupport](../common/src/main/java/com/example/interview/common/JwtSupport.java).
It constrains verification to the public RSA key and validates issuer,
timestamps, audience, required expiry and UUID subject. Library timestamp
validation allows clock skew; synchronize clocks and understand that a tiny
expiry boundary is not necessarily rejected at the exact second.

## Gateway checks and service checks

The gateway verifies bearer tokens for protected routes and requires the
matching scope. Resource services do the same independently. The forwarded
Authorization header is not replaced with a user ID header that services
blindly trust.

**Authentication:** is this a valid token from our issuer?
**Capability authorization:** may this identity use the orders API?
**Object authorization:** is this particular order owned by the identity?

`GET /orders/{id}` filters by **both order ID and token subject**. A different
user gets 404 to avoid revealing resource existence. Lists and notifications
are likewise owner-scoped. Guessing an ID does not grant access.

## Why CSRF is disabled in this specific lab

The APIs use explicit Authorization headers, no form-login session and no
authentication cookies. There are no ambient browser credentials automatically
attached by the browser. That is the reason for disabling CSRF here.

If you move tokens into cookies, add session login or build a browser frontend,
reconsider CSRF and CORS based on that design. CORS is not authentication.
Do not copy "disable CSRF" into every application.

## Token lifecycle and important missing features

There are no refresh tokens or server-side access-token revocation. Re-login
after 15 minutes. "Logout" by deleting a client token does not invalidate a
stolen copy before expiry. There is no password-reset, email verification,
MFA, account lockout, login rate limiting or signing-key rotation workflow.

Production identity systems need deliberate solutions for those concerns;
using an established identity provider is often safer and cheaper than
maintaining your own. To extend this educational implementation, start with
one feature and its security tests rather than inventing an all-in-one auth
protocol.

A refresh-token design would require high-entropy opaque tokens, storing their
hashes, expiry/revocation, transactional rotation and reuse detection.
Do not simply create a non-expiring JWT.

## Local key handling

`init-local.sh` generates keys on your machine, never embeds them in Git or an
image. The private file remains owner-readable. Compose runs auth with the
local UID/GID so its container can read the file; other services receive only
the public file. This is a local bind-mount solution, not a key vault.

HTTP and Kafka transport are unencrypted inside this local lab. Bind the
gateway to localhost and do not expose it to the internet. Deployment needs
TLS, secret management, hardened network policy and operational monitoring.

## Interview self-check

Explain hashing versus encryption versus signing, 401 versus 403, and why
scope does not prove ownership. Explain why a database lookup is needed for
login but not for each ordinary JWT verification, and what trade-off that
creates for immediate revocation.
