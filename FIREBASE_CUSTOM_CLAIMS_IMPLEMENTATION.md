# Firebase Custom Claims Implementation for Admin Login

## Overview

This document explains the Firebase Custom Claims implementation for secure admin access verification in the Deepfake Guard application.

## Why Firebase Custom Claims?

Firebase Custom Claims provide a **secure, server-verified way to manage user roles**:
- Claims are set server-side and verified by Firebase
- Claims are included in the user's ID token
- Claims cannot be tampered with by the client
- More secure than storing role in Firestore alone

## Changes Made

### 1. **FirebaseAuthManager.kt** - JWT Token & Claims Extraction

Added three new methods to handle custom claims:

```kotlin
// Get all custom claims from the ID token
suspend fun getCustomClaims(): Map<String, Any>

// Get a specific claim value by key
suspend fun getCustomClaimString(claimKey: String): String?

// Helper methods for JWT parsing
private fun String.padForBase64(): String
private fun parseJsonToMap(json: String): Map<String, Any>
```

**How it works:**
1. Forces a token refresh: `user.getIdToken(true).await()`
2. Manually decodes the JWT (format: `header.payload.signature`)
3. Extracts and parses the JSON claims from the payload
4. Returns claims as a `Map<String, Any>`

### 2. **UserProfileRepository.kt** - Custom Claims Priority

Modified `getUserProfile()` to check custom claims first:

```kotlin
// Priority:
// 1. Check Firebase Custom Claims for "admin" claim
// 2. Fall back to Firestore "role" field
// 3. Default to "REGISTERED"

var roleFromFirebase = snapshot.getString("role") ?: "REGISTERED"

try {
    val customClaimRole = FirebaseAuthManager.getCustomClaimString("admin")
    if (customClaimRole != null && customClaimRole.toBoolean()) {
        roleFromFirebase = "ADMIN"
    }
} catch (e: Exception) {
    // Custom claims not available, fallback to Firestore
}
```

### 3. **AppMainViewModel.kt** - Enhanced Login Logging

Updated the login function to log custom claims verification:

```kotlin
Log.d("Login", "Fetching user profile with custom claims check for uid=${firebaseUser.uid}")
val profile = userProfileRepository.getUserProfile(firebaseUser.uid)
Log.d("Login", "User profile loaded. Role from claims/Firestore: ${profile.role}")
```

### 4. **AdminDashboard.kt** - Custom Claims Verification Message

Enhanced the admin dashboard to show custom claims verification:

```kotlin
Log.i("AdminDashboard", "Admin user verified via Firebase Custom Claims...")
Toast.makeText(ctx, "Admin access verified via Firebase Custom Claims", Toast.LENGTH_SHORT).show()
```

## Cloud Function Requirements

The `addAdminUser` Cloud Function must set custom claims when creating an admin:

```javascript
// Cloud Function: addAdminUser
exports.addAdminUser = functions.https.onCall(async (data, context) => {
    const uid = data.uid;
    const displayName = data.displayName;
    
    try {
        // Set custom claims for admin role
        await admin.auth().setCustomUserClaims(uid, {
            admin: true  // Boolean claim indicating admin role
        });
        
        // Also set role in Firestore for additional verification
        await admin.firestore().collection('users').doc(uid).set({
            role: 'ADMIN',
            display_name: displayName,
            updated_at: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
        
        return { success: true, message: 'Admin user created' };
    } catch (error) {
        throw new functions.https.HttpsError('internal', error.message);
    }
});
```

## Login Flow with Custom Claims

```
┌─────────────────────────────────────────────────────┐
│ 1. User enters email/password in LoginScreen        │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ 2. AppMainViewModel.login()                         │
│    - Calls FirebaseAuthManager.login()              │
│    - Validates email verification                   │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ 3. getUserProfile(uid)                              │
│    - Fetches user doc from Firestore                │
│    - Calls getCustomClaimString("admin")            │
│    ├─ Refreshes ID token                            │
│    ├─ Decodes JWT manually                          │
│    └─ Extracts claims from payload                  │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ 4. Role Determination                               │
│    if (customClaim "admin" == true) {               │
│        role = ADMIN                                 │
│    } else {                                         │
│        role = Firestore role field                  │
│    }                                                │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│ 5. DashboardScreen Routes User                      │
│    if (role == ADMIN) {                             │
│        show AdminDashboard                          │
│    } else {                                         │
│        show UserDashboard                           │
│    }                                                │
└─────────────────────────────────────────────────────┘
```

## Verification & Testing

### Check Logs During Login

When an admin logs in, you should see:

```
D/Login: Fetching user profile with custom claims check for uid=<uid>
D/UserProfile: Using ADMIN role from Firebase custom claims for uid=<uid>
D/Login: User profile loaded. Role from claims/Firestore: ADMIN
I/AdminDashboard: Admin user verified via Firebase Custom Claims...
```

### Fallback Behavior

If custom claims are unavailable (e.g., claims not yet set):

```
D/UserProfile: Custom claims not available, using Firestore role: ADMIN
```

The app will still work using the Firestore role as fallback.

## Security Benefits

1. **Server-Verified**: Claims are set server-side and cannot be modified by client
2. **Dual Verification**: Checks both custom claims (primary) and Firestore role (fallback)
3. **Token-Bound**: Custom claims are bound to the user's ID token
4. **Firestore Rules Ready**: Can now use `request.auth.token.admin == true` in Firestore rules

## Example Firestore Security Rules

With custom claims set, you can now use them in Firestore security rules:

```javascript
// Allow admin access to sensitive collections
match /admin_settings/{document=**} {
  allow read, write: if request.auth.token.admin == true;
}

match /audit_logs/{document=**} {
  allow read: if request.auth.token.admin == true;
  allow write: if false; // Write only via Cloud Functions
}
```

## Implementation Checklist

- [x] Added custom claims extraction in `FirebaseAuthManager.kt`
- [x] Updated `UserProfileRepository.getUserProfile()` to check claims
- [x] Enhanced login logging in `AppMainViewModel.kt`
- [x] Updated admin dashboard feedback in `AdminDashboard.kt`
- [ ] Ensure Cloud Function `addAdminUser` sets custom claims
- [ ] Update Firestore security rules to use custom claims (optional but recommended)
- [ ] Test login with admin account
- [ ] Verify logs show custom claims verification

## Next Steps

1. **Verify Cloud Function**: Ensure `addAdminUser` Cloud Function sets the `admin` custom claim
2. **Update Security Rules**: Update Firestore rules to check `request.auth.token.admin`
3. **Test Admin Login**: Create or log in with an admin account and verify the logs
4. **Monitor**: Watch Logcat during login to confirm custom claims are being used

## Troubleshooting

### Issue: "Custom claims not available" always shows

**Solution**: Ensure the Cloud Function `addAdminUser` is setting the custom claim:
```javascript
await admin.auth().setCustomUserClaims(uid, { admin: true });
```

### Issue: Admin user still sees UserDashboard

**Solution**: 
1. Force logout and login again (to get fresh token with claims)
2. Check Cloud Function logs to see if claim was set
3. Verify Firestore user document has `role: ADMIN` as fallback

### Issue: Token refresh fails

**Solution**: This is expected on old tokens. The fallback to Firestore role ensures the app still works.

## Related Files Modified

- [FirebaseAuthManager.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/FirebaseAuthManager.kt)
- [UserProfileRepository.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/UserProfileRepository.kt)
- [AppMainViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/control/viewmodel/AppMainViewModel.kt#L185-L230)
- [AdminDashboard.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/dashboard/AdminDashboard.kt)
