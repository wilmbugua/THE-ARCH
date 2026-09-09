# KALC POS - User Login Credentials

## ✅ PIN Login Now Working!

The backend authentication is fully functional. Here are the **correct login credentials** for all user roles:

### Default Users & PINs

| Username | PIN | Role | Access Level |
|----------|-----|------|--------------|
| **admin** | **11112222** | Administrator | Full system access |
| **manager** | **22223333** | Manager | Manager functions |
| **super_waiter** | **33334444** | Super Waiter | Super Waiter privileges |
| **waiter** | **44445555** | Waiter | Waiter functions |
| **supervisor** | **55556666** | Supervisor | Supervisor access |

## How to Login

### API (Backend)
- **Endpoint:** `POST /api/v1/auth/pin-login`
- **Content-Type:** `application/json`

**Request Example:**
```json
{
  "username": "admin",
  "pin": "11112222"
}
```

**Successful Response:**
```json
{
  "token": "cbe56ab6cc7a5e9f64542c13451edb9729f20a96f7f67dead3787ee1a19f0780",
  "user": {
    "id": 1,
    "username": "admin",
    "role": "admin",
    "name": "admin"
  }
}
```

The response includes an auth token valid for 8 hours.

### Web Interface (Frontend)
Use the PIN login screen in the KALC POS front-end application and enter:
- Username
- PIN (8-digit code)

## Session Management

After successful login:
- Your **authentication token** is returned and should be stored
- Token is valid for **8 hours** (480 minutes)
- Use token in subsequent API calls with header: `Authorization: Bearer <token>`

### Verify Session
- **Endpoint:** `GET /api/v1/auth/verify`
- **Header:** `Authorization: Bearer <your_token>`

### Logout
- **Endpoint:** `POST /api/v1/auth/logout`
- **Header:** `Authorization: Bearer <your_token>`

## Troubleshooting

### "Invalid username or PIN" Error
- Double-check the **PIN is exactly 8 digits**
- Verify you're using the correct PIN from the table above
- Ensure the username matches (case-sensitive)
- Check that the user account is **active** (default: yes)

### Account Not Found
- If a user doesn't exist, verify they're in the default users list
- New users must be created through the system administrator
- Contact your system administrator to add new users

## Backend Health Check

- **Endpoint:** `GET /api/v1/auth/health`
- **Response:** `{"status":"ok"}` - indicates backend is running


