# Camera Access Improvement Plan

## Problem
The user is encountering `TypeError: Cannot read properties of undefined (reading 'getUserMedia')`.
This occurs because modern browsers **only** expose `navigator.mediaDevices` in **Secure Contexts** (HTTPS or localhost). On a "live server" running over HTTP, this API is undefined.

## Proposed Changes
1.  **Update `employee-dashboard.html`**:
    - Add a check for `navigator.mediaDevices` before accessing it.
    - If undefined, display a user-friendly alert explaining that the camera requires a secure connection (HTTPS).
2.  **Update `employee-face-capture.html`**:
    - Apply the same robust check and error messaging.

## Verification
- Verify code syntax.
- User will need to deploy to verify (or use a non-secure local IP to reproduce, e.g., `http://192.168.x.x`).
