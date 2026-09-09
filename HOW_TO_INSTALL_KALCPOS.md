# KALCPOS New Server Installation Guide

Use this guide to install KALCPOS on a Windows server computer and let other computers access the POS through a browser.

## Required Apps

Install these on the server computer:

| App | Required version | Notes |
| --- | --- | --- |
| Windows | Windows 10/11 or Windows Server 2016+ | Server computer that will run the POS. |
| Java | Java 17 or newer | Required to run `backend\webpos-backend-0.0.1-SNAPSHOT.jar`. Eclipse Temurin 17 LTS is recommended. |
| MariaDB | MariaDB 10.5 or newer | Recommended database. MariaDB 10.11 LTS is a good choice. |
| MySQL | MySQL 8.0 or newer | Alternative to MariaDB. Use either MariaDB or MySQL, not both for the POS database. |
| Browser | Chrome, Edge, or Firefox current version | Used by the server and client computers to open the POS. |
| PowerShell | Windows PowerShell 5.1 or PowerShell 7+ | Windows PowerShell 5.1 is included with Windows. |

Developer-only apps, not needed for normal installation:

| App | Version | When needed |
| --- | --- | --- |
| Maven | 3.9+ | Only if rebuilding the backend from source. |
| JDK | 17+ | Only if compiling source. Normal install only needs Java Runtime 17+. |

## Install Java 17

1. Download and install Eclipse Temurin JRE/JDK 17 LTS for Windows x64.
2. Open PowerShell and verify:

```powershell
java -version
```

Expected result: version `17.x` or newer.

## Install MariaDB

1. Install MariaDB 10.5+ or 10.11 LTS.
2. During setup, set and remember the root password.
3. Set the MariaDB service to start automatically.
4. Verify the service is running:

```powershell
Get-Service MariaDB
```

Expected result: `Status` is `Running` and `StartType` is `Automatic`.

## Extract KALCPOS

1. Extract `KALCPOS_NEWINSTALL.zip`.
2. Move the extracted folder to:

```text
D:\KALCPOS
```

## Create Database

Open MariaDB/MySQL command prompt or PowerShell where `mysql.exe` is available.

```powershell
mysql -u root -p
```

Then run:

```sql
CREATE DATABASE IF NOT EXISTS kalc_pos_web CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
```

Load the schema and default users:

```powershell
cd D:\KALCPOS
mysql -u root -p kalc_pos_web < database\run_migrations.sql
mysql -u root -p kalc_pos_web < database\init_users.sql
```

## Configure Database Login

Default configuration:

| Setting | Default |
| --- | --- |
| Database host | `localhost` |
| Database port | `3306` |
| Database name | `kalc_pos_web` |
| Database user | `root` |
| Database password | `Kaisy@3030` |
| Backend port | `8081` |
| Browser app port | `8000` |

If your database password is different, install auto-start with your password:

```powershell
cd D:\KALCPOS
.\INSTALL_KALCPOS_AUTOSTART.ps1 -DbUser "root" -DbPassword "YOUR_DATABASE_PASSWORD"
```

## Start KALCPOS

For normal startup:

```powershell
cd D:\KALCPOS
.\START_KALCPOS_AUTO.ps1
```

Or double-click:

```text
INSTALL_AND_START_KALCPOS.bat
```

The install script registers this Windows scheduled task:

```text
KALCPOS Auto Start
```

After computer restart, KALCPOS starts automatically when the Windows user logs in.

## Open POS

On the server computer:

```text
http://127.0.0.1:8000
```

On another computer on the same network:

```text
http://SERVER_IP_ADDRESS:8000
```

Find the server IP address:

```powershell
ipconfig
```

## Connect Client Computers to the Server

Client computers do not connect directly to the database. They connect to the KALCPOS web server in a browser, and the server backend connects to MariaDB/MySQL.

Recommended network layout:

```text
Client browser -> http://SERVER_IP:8000 -> KALCPOS frontend proxy -> backend on 8081 -> MariaDB on server localhost:3306
```

Steps:

1. Install KALCPOS, Java, and MariaDB only on the server computer.
2. Make sure the server and all client computers are on the same local network.
3. On the server, find the IPv4 address:

```powershell
ipconfig
```

Look for an address like:

```text
192.168.1.50
```

4. On each client computer, open Chrome, Edge, or Firefox.
5. Browse to:

```text
http://192.168.1.50:8000
```

Replace `192.168.1.50` with the actual server IP address.

6. Bookmark that URL on each client computer.

Important:

- Keep MariaDB bound to the server computer unless there is a specific administration need.
- Do not share the database root password with client users.
- Client computers only need browser access to port `8000`.
- If the server IP changes after restart, set a static IP address on the server or reserve the IP in the router DHCP settings.
- If clients cannot open the POS, check Windows Firewall on the server and allow TCP port `8000`.

Optional direct backend access:

If a client must call backend APIs directly, use:

```text
http://SERVER_IP_ADDRESS:8081
```

Only open port `8081` to trusted local network devices.

## Allow Firewall

Run PowerShell as Administrator:

```powershell
New-NetFirewallRule -DisplayName "KALCPOS 8000" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow
```

If clients need direct backend access, also allow port `8081`:

```powershell
New-NetFirewallRule -DisplayName "KALCPOS Backend 8081" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow
```

## Default Login PINs

| Username | PIN | Role |
| --- | --- | --- |
| `admin` | `11112222` | Administrator |
| `manager` | `22223333` | Manager |
| `super_waiter` | `33334444` | Super Waiter |
| `waiter` | `44445555` | Waiter |
| `supervisor` | `55556666` | Supervisor |

Change default PINs before production use.

## Health Checks

Backend health:

```text
http://127.0.0.1:8081/health
```

Frontend:

```text
http://127.0.0.1:8000
```

## Logs

Backend log:

```text
D:\KALCPOS\logs\kalcpos-backend.log
```

Frontend proxy log:

```text
D:\KALCPOS\frontend\proxy_requests.log
```

## Stop KALCPOS

Use Task Manager to stop Java and PowerShell proxy processes, or restart the computer.

To disable automatic startup, open Task Scheduler and disable or delete:

```text
KALCPOS Auto Start
```

## Modify or Add Features in Future

The install package includes the runnable app and source code needed for backend changes. Keep one working backup zip before making changes.

Main folders:

| Folder/file | Purpose |
| --- | --- |
| `src\main\java\com\kalcpos` | Backend Spring Boot source code. |
| `src\main\resources\application.properties` | Backend runtime settings. |
| `frontend` | Static frontend files served to browsers. |
| `database` | SQL schema and starter data scripts. |
| `backend\webpos-backend-0.0.1-SNAPSHOT.jar` | Runnable backend jar used by startup scripts. |
| `START_KALCPOS_AUTO.ps1` | Starts backend and frontend proxy. |

Required developer apps:

| App | Version |
| --- | --- |
| JDK | 17 or newer |
| Maven | 3.9 or newer |
| Code editor | IntelliJ IDEA, VS Code, or similar |
| MariaDB/MySQL client | MariaDB 10.5+ client or MySQL 8.0+ client |

Backend change workflow:

1. Stop the running POS backend if it is already running.
2. Edit Java files under:

```text
D:\KALCPOS\src\main\java
```

3. Build the backend:

```powershell
cd D:\KALCPOS
mvn clean package
```

4. Replace the runtime jar:

```powershell
Copy-Item target\webpos-backend-0.0.1-SNAPSHOT.jar backend\webpos-backend-0.0.1-SNAPSHOT.jar -Force
```

5. Start KALCPOS:

```powershell
.\START_KALCPOS_AUTO.ps1
```

6. Verify:

```text
http://127.0.0.1:8081/health
http://127.0.0.1:8000
```

Frontend change workflow:

1. Edit files under:

```text
D:\KALCPOS\frontend
```

2. Restart the frontend proxy if needed.
3. Hard refresh the browser:

```text
Ctrl + F5
```

4. If changing files that are also copied into extracted static folders, copy the updated files to the matching runtime static location or rebuild the package.

Database change workflow:

1. Back up the database before schema changes.
2. Add SQL changes to:

```text
D:\KALCPOS\database
```

3. Test on a copy of the database before production.
4. Apply with:

```powershell
mysql -u root -p kalc_pos_web < database\YOUR_SCRIPT.sql
```

Feature change checklist:

- Confirm the backend builds with `mvn clean package`.
- Confirm login works with the default/admin account.
- Confirm the changed screen works from the server browser.
- Confirm a client computer can still open `http://SERVER_IP:8000`.
- Keep a dated zip backup before replacing a working installation.

Recommended backup command:

```powershell
Compress-Archive -Path D:\KALCPOS\* -DestinationPath D:\KALCPOS_BACKUP_YYYYMMDD.zip
```

## Troubleshooting

- If `java -version` is not found, reinstall Java 17 and reopen PowerShell.
- If login fails after install, confirm MariaDB is running and the DB password in `KALCPOS_AUTO_CONFIG.ps1` is correct.
- If another app uses port `8000`, stop that app before running `START_KALCPOS_AUTO.ps1`.
- If another app uses port `8081`, run backend with a different server port and update the frontend proxy backend URL.
- Browser restart keeps the current login session while the backend token is still valid. The token validity is 8 hours.

