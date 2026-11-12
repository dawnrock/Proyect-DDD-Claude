# Deploy on Render

How to publish this branch as a web service on [Render](https://render.com)'s free plan (#85). The layered monolith is deployed the same way from branch `Monolithic` (#86), as a second, separate service.

Render runs Java only through Docker, so the repository has a [`Dockerfile`](../../Dockerfile). The application code is the same as in local: everything that changes for the deployment is set by the image or by environment variables.

| What | Set by | Value |
| ---- | ------ | ----- |
| Demo data | image (`SPRING_PROFILES_ACTIVE`) | `demo` profile: three accounts and their operations, rebuilt on every start |
| Port | Render (`PORT`) | The container listens on `PORT` (8080 when it is not set) |
| Memory | image (`JAVA_TOOL_OPTIONS`) | The JVM uses up to 75 % of the container (512 MB on the free plan) |
| H2 console | image (`SPRING_H2_CONSOLE_ENABLED=false`) | Off: on a public URL it could run code on the server |
| Users and passwords | you, in Render (`APP_SECURITY_USERS_*`) | Your own password instead of `demo` |

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
   - **Keep the four user names.** The demo accounts belong to `admin`, `ada`, `grace` and `alan`; with other names the customers would see no account. Each user may have a different hash.
   - Optional: `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` add one more administrator (the password in plain text, stored in Render).

5. **Create Web Service.** The first build takes a few minutes (Maven downloads the dependencies); the service is live when the health check answers `UP`.

## 3. Check it

```bash
curl https://<your-service>.onrender.com/actuator/health
```

```bash
curl -u admin:'your-password' https://<your-service>.onrender.com/api/accounts
```

The first answers `{"status":"UP",...}`; the second lists the three demo accounts. `admin:demo` must answer `401`. In the browser, `https://<your-service>.onrender.com` shows the login page: `admin` sees every account, `ada`, `grace` and `alan` only their own.

## What to expect on the free plan

Render's free conditions change from time to time: check the current plan on its website. At the time of writing:

- **Sleep.** After about 15 minutes without requests the service sleeps; the next request wakes it up, which takes about a minute.
- **Data in memory.** Every start rebuilds the demo data, so accounts opened or operations made on the site disappear when the service sleeps or is redeployed. This is a practice project with simulated persistence.
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
