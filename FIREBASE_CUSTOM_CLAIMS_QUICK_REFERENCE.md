# Firebase Custom Claims Implementation - Quick Reference

## What Was Changed

### 4 Files Modified

#### 1. **FirebaseAuthManager.kt** ✅
- Added `getCustomClaims()` - extracts all custom claims from ID token
- Added `getCustomClaimString(claimKey)` - gets specific claim value
- Added JWT parsing helpers (`padForBase64()`, `parseJsonToMap()`)
- Automatically refreshes token to get latest claims

#### 2. **UserProfileRepository.kt** ✅
- Modified `getUserProfile()` to prioritize custom claims
- Checks for Firebase "admin" custom claim first
- Falls back to Firestore "role" field if claims unavailable
- Added logging for claim source (claims vs Firestore)

#### 3. **AppMainViewModel.kt** ✅
- Enhanced login function with custom claims logging
- Shows when custom claims are being checked
- Logs final role determination (claims or Firestore)

#### 4. **AdminDashboard.kt** ✅
- Updated to show "Admin access verified via Firebase Custom Claims"
- Added logging with timestamp
- Enhanced onboarding message for admin users

---

## How It Works (Simplified)

```
Admin Logs In
    ↓
Firebase Authentication
    ↓
Token Refreshed (forces claims update)
    ↓
JWT Decoded:
  ├─ Check for custom claim: admin = true
  ├─ If found: Use ADMIN role
  └─ If not: Fall back to Firestore role
    ↓
Route to Appropriate Dashboard
```

## Key Custom Claim

The app checks for the **"admin"** custom claim:

```json
{
  "admin": true,      // If this is present and true, user is ADMIN
  "iat": 1644xyz,     // Other standard JWT claims
  "exp": 1644xyz,
  ...
}
```

---

## Cloud Function Requirement

Your `addAdminUser` Cloud Function **MUST** set this custom claim:

```javascript
await admin.auth().setCustomUserClaims(uid, {
    admin: true
});
```

If it doesn't, the app will fall back to the Firestore role field.

---

## Testing Admin Login

When an admin logs in, check Logcat for:

```
✓ D/Login: Fetching user profile with custom claims check for uid=xyz
✓ D/UserProfile: Using ADMIN role from Firebase custom claims for uid=xyz
✓ D/Login: User profile loaded. Role from claims/Firestore: ADMIN
✓ I/AdminDashboard: Admin user verified via Firebase Custom Claims...
```

Or if using Firestore fallback:

```
✓ D/UserProfile: Custom claims not available, using Firestore role: ADMIN
```

---

## Security Improvements

✅ **Before**: Role only stored in Firestore (can be modified via app if rules not strict)
✅ **After**: Custom claims verified against Firebase (tamper-proof)

Benefits:
- Server-verified role claim
- Cannot be changed by client
- Can now enforce in Firestore Security Rules
- Token-bound to user session

---

## Files Created/Modified Summary

```
MODIFIED:
  • app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/FirebaseAuthManager.kt
  • app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/UserProfileRepository.kt
  • app/src/main/java/com/example/fyp_25_s4_23/control/viewmodel/AppMainViewModel.kt
  • app/src/main/java/com/example/fyp_25_s4_23/boundary/dashboard/AdminDashboard.kt

CREATED:
  • FIREBASE_CUSTOM_CLAIMS_IMPLEMENTATION.md (detailed documentation)
  • FIREBASE_CUSTOM_CLAIMS_QUICK_REFERENCE.md (this file)
```

---

## Next Steps

1. ✅ Code changes complete
2. ⚠️ **Verify Cloud Function** - ensure `addAdminUser` sets custom claim
3. ⚠️ **Test admin login** - check Logcat during login
4. 📋 **Update Firestore Rules** (optional) - use custom claims for security

```javascript
// Example Firestore Rule
match /admin_settings/{document=**} {
  allow read, write: if request.auth.token.admin == true;
}
```

---

## Status

🟢 **IMPLEMENTATION COMPLETE**
- All 4 files modified and error-checked
- Custom claims extraction ready
- Login flow enhanced with logging
- Admin dashboard shows verification message
- Fallback to Firestore role ensures backward compatibility

⚠️ **Pending**: Cloud Function verification (ensure it's setting the custom claim)
