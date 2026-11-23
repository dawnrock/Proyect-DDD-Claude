# Deploy on Render

How to publish this branch as a web service on [Render](https://render.com)'s free plan (#85). The layered monolith is deployed the same way from branch `Monolithic` (#86), as a second, separate service.

Render runs Java only through Docker, so the repository has a [`Dockerfile`](../../Dockerfile). The application code is the same as in local: everything that changes for the deployment is set by the image or by environment variables.

| What | Set by | Value |
| ---- | ------ | ----- |
| Demo data | image (`SPRING_PROFILES_ACTIVE`) | `demo` profile: three accounts and their operations, loaded when the database has no accounts |
| Database (`Hexagonal`) | you, in Render (`SPRING_PROFILES_ACTIVE=demo,prod` and `SPRING_DATASOURCE_*`) | PostgreSQL on Neon, so data survives restarts (#150, ADR 0019); without them, H2 in memory |
| Port | Render (`PORT`) | The container listens on `PORT` (8080 when it is not set) |
| Memory | image (`JAVA_TOOL_OPTIONS`) | The JVM uses up to 75 % of the container (512 MB on the free plan) |
| H2 console | image (`SPRING_H2_CONSOLE_ENABLED=false`) | Off: on a public URL it could run code on the server |
| Users and passwords | you, in Render (`APP_SECURITY_USERS_*`) | Your own password instead of `demo` |
| API token secret | you, in Render (`APP_JWT_SECRET`, `Hexagonal` only) | A random value of at least 32 bytes (#122, ADR 0015) |

**Live services** (given by the user, 2026-11-17): `Hexagonal` at <https://proyect-ddd-claude.onrender.com> (also in the `servers` of `docs/api/openapi.yaml`) and `Monolithic` at <https://mon-e-bank-monolithic.onrender.com>. On the free plan they sleep when idle, so the first request may take about a minute. The image also serves the API documentation: Swagger UI at `/api-docs` over the contract at `/openapi.yaml` (#111; the `Dockerfile` copies `docs/api/openapi.yaml` into the build).

## 1. Choose a password and get its hash

The `demo` password is in the README: on a public URL anyone could sign in. Choose your own password and get its **BCrypt hash** (the application only stores hashes). Any of these works:

```bash
htpasswd -nbBC 10 "" 'your-password' | tr -d ':\n'
```

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'your-password' | tr -d ':\n'
```

```bash
pip install bcrypt
```

```bash
python -c "import bcrypt; print(bcrypt.hashpw(b'your-password', bcrypt.gensalt(10)).decode())"
```

The result starts with `$2y$10$` or `$2b$10$` and is about 60 characters long. Run it on your own computer: do not paste the password into a website.

## 2. Create the web service

1. Sign up at Render with your GitHub account (the free plan needs no card).
2. **New → Web Service**, and pick this repository.
3. Settings:

   | Field | Value |
   | ----- | ----- |
   | Name | e.g. `ddd-hexagonal` (it becomes `https://ddd-hexagonal.onrender.com`; Render adds a suffix if the name is taken) |
   | Branch | `Hexagonal` (`Monolithic` for the phase-1 service) |
   | Language / Runtime | Docker |
   | Instance type | Free |
   | Health check path (Advanced) | `/actuator/health` |

4. **Environment variables.** Add the twelve below (Render's **Add from .env** accepts them pasted all at once), with your hash in the four `PASSWORDHASH` lines:

   ```text
   APP_SECURITY_USERS_0_USERNAME=admin
   APP_SECURITY_USERS_0_PASSWORDHASH=<hash>
   APP_SECURITY_USERS_0_ROLE=ADMIN
   APP_SECURITY_USERS_1_USERNAME=ada
   APP_SECURITY_USERS_1_PASSWORDHASH=<hash>
   APP_SECURITY_USERS_1_ROLE=USER
   APP_SECURITY_USERS_2_USERNAME=grace
   APP_SECURITY_USERS_2_PASSWORDHASH=<hash>
   APP_SECURITY_USERS_2_ROLE=USER
   APP_SECURITY_USERS_3_USERNAME=alan
   APP_SECURITY_USERS_3_PASSWORDHASH=<hash>
   APP_SECURITY_USERS_3_ROLE=USER
   ```

   - **All twelve are needed.** Spring Boot replaces the whole user list when a variable sets any part of it: with only the hashes the users would have no name and the application would not start.
   - **Keep the four user names.** (Customers who sign up on the site get their own user and need no variable.) The demo accounts belong to `admin`, `ada`, `grace` and `alan`; with other names the customers would see no account. Each user may have a different hash.
   - Optional: `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` add one more administrator (the password in plain text, stored in Render).
   - **`Hexagonal` only: `APP_JWT_SECRET`**, the secret that signs the API tokens (#122). Use Render's **Generate** button, or `openssl rand -base64 48` on your computer; it must be at least 32 bytes. Without it the service starts with a random key and every token stops working when it sleeps or restarts. Changing it invalidates every token at once. **Never deploy without it:** the image runs the `demo` profile, whose development secret is public in the repository, so anyone could forge an administrator token.

   - **`Hexagonal` only, once the Neon database exists ([section 2 bis](#2-bis-database-on-neon-hexagonal)):** `SPRING_PROFILES_ACTIVE=demo,prod` and the three `SPRING_DATASOURCE_*` variables.

5. **Create Web Service.** The first build takes a few minutes (Maven downloads the dependencies); the service is live when the health check answers `UP`.

## 2 bis. Database on Neon (`Hexagonal`)

Since #150 (ADR 0019) the `Hexagonal` service keeps users, customers, accounts and transactions in PostgreSQL managed by [Neon](https://neon.com), on its free plan. Without this section the service still works, with H2 in memory as before. `Monolithic` does not change.

1. **Sign up at Neon** (GitHub or Google account; the free plan needs no card).
2. **Create a project** in the region **AWS Europe Central 1 (Frankfurt), `eu-central-1`**, the same as the Render services, so every query stays inside Frankfurt. The default PostgreSQL version and the database `neondb` are fine.
3. **Get the connection details:** in the project dashboard, **Connect**, with **Connection pooling off** (the direct connection: the pooled one, PgBouncer in transaction mode, does not get on with JDBC prepared statements). The connection string looks like `postgresql://USER:PASSWORD@ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require`. Keep the values in a password manager; never in the repository or a chat.
4. **In Render, Environment of the `Hexagonal` service**, add:

   ```text
   SPRING_PROFILES_ACTIVE=demo,prod
   SPRING_DATASOURCE_URL=jdbc:postgresql://ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require
   SPRING_DATASOURCE_USERNAME=<user, usually neondb_owner>
   SPRING_DATASOURCE_PASSWORD=<password>
   ```

   The URL is the connection string with `jdbc:` in front and without the user and password. **Add them only once the deployed code includes #150:** older code also reads `SPRING_DATASOURCE_URL` and would fail to start without the PostgreSQL driver.
5. **Save, rebuild and deploy.** On the first start Flyway creates the tables and the `demo` profile loads the demo data; later starts find the accounts and load nothing. The `prod` profile has no defaults: if a variable is missing, the service does not start instead of silently using H2.

The configured users (`APP_SECURITY_USERS_*`, `APP_ADMIN_*`) are written to the database on every start: changing a hash in Render still changes that user's password. Users who signed up are kept.

## 3. Check it

```bash
curl https://<your-service>.onrender.com/actuator/health
```

```bash
TOKEN=$(curl -s -X POST https://<your-service>.onrender.com/api/auth/token -H 'Content-Type: application/json' -d '{"username":"admin","password":"your-password"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
```

```bash
curl -H "Authorization: Bearer $TOKEN" https://<your-service>.onrender.com/api/accounts
```

The first answers `{"status":"UP",...}`; the other two get a token and list the three demo accounts (on `Monolithic` the API still uses HTTP Basic: `curl -u admin:'your-password' .../api/accounts`). With the twelve variables set, asking for a token with `admin`/`demo` must answer `401`. In the browser, `https://<your-service>.onrender.com` shows the login page: `admin` sees every account, `ada`, `grace` and `alan` only their own.

## What to expect on the free plan

Render's free conditions change from time to time: check the current plan on its website. At the time of writing:

- **Sleep.** After about 15 minutes without requests the service sleeps; the next request wakes it up, which takes about a minute.
- **Data.** With Neon (`Hexagonal`, [section 2 bis](#2-bis-database-on-neon-hexagonal)) users, customers, accounts and transactions survive sleeps and redeploys; to check it, sign up a test customer, restart the service (**Manual Deploy → Restart service**) and log in with that customer. Without Neon, and always on `Monolithic`, data lives in memory: every start rebuilds the demo data and what was done on the site disappears.
- **Neon also sleeps.** Its compute suspends after about 5 minutes idle; the first request after a pause pays that start (about a second) on top of Render's. The free plan's storage (about 1 GB at the time of writing) is far more than the demo needs.
- **Monthly hours.** The free instance hours are shared by every free service of the account; two services that sleep when idle fit in them.

## Redeploy and stop

- **Redeploy:** every push (or merge) to the service's branch deploys again (*Auto-Deploy*, on by default). **Manual Deploy** redeploys by hand.
- **Change the password:** edit the variables in **Environment**; Render restarts the service.
- **Stop:** **Settings → Suspend Web Service** (it can be resumed) or **Delete Web Service**.

## Build the image locally (optional)

With Docker installed:

```bash
docker build -t ddd-hexagonal .
```

```bash
docker run --rm -p 10000:10000 -e PORT=10000 ddd-hexagonal
```

Then open `http://localhost:10000` (users with password `demo`, since no `APP_SECURITY_USERS_*` variable is set).
