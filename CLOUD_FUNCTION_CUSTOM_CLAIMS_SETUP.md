# Firebase Custom Claims - Cloud Function Setup Guide

## Current Implementation

Your app now supports Firebase Custom Claims for admin verification. The next critical step is ensuring your Cloud Function is properly setting the custom claim when creating admin users.

## Cloud Function: `addAdminUser`

### Current Status

You likely have a Cloud Function that creates admin users. It needs to include the custom claims setup.

### Required Implementation

Your `addAdminUser` Cloud Function should look like this:

```javascript
const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

exports.addAdminUser = functions.https.onCall(async (data, context) => {
  // Verify caller is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  // Verify caller is an admin
  const callerClaims = context.auth.token;
  if (!callerClaims.admin) {
    throw new functions.https.HttpsError('permission-denied', 'Only admins can create other admins');
  }

  const uid = data.uid;
  const displayName = data.displayName;
  const role = data.role || 'ADMIN';

  try {
    // 1. SET CUSTOM CLAIMS (Most Important!)
    await admin.auth().setCustomUserClaims(uid, {
      admin: true,
      role: role,
      createdAt: new Date().toISOString()
    });
    console.log(`Custom claims set for admin user ${uid}`);

    // 2. Update Firestore user document (Fallback + Metadata)
    await admin.firestore().collection('users').doc(uid).set({
      role: 'ADMIN',
      display_name: displayName,
      updated_at: admin.firestore.FieldValue.serverTimestamp(),
      admin_created_at: new Date().toISOString()
    }, { merge: true });
    console.log(`Firestore document created/updated for user ${uid}`);

    // 3. Create admin metadata document (Optional but recommended)
    await admin.firestore().collection('admin_users').doc(uid).set({
      uid: uid,
      displayName: displayName,
      createdAt: new Date().toISOString(),
      createdBy: context.auth.uid
    });
    console.log(`Admin metadata created for user ${uid}`);

    return {
      success: true,
      message: `Admin user ${uid} created successfully`,
      data: {
        uid: uid,
        displayName: displayName,
        role: 'ADMIN',
        customClaimSet: true
      }
    };

  } catch (error) {
    console.error('Error in addAdminUser:', error);
    throw new functions.https.HttpsError('internal', 
      `Failed to create admin user: ${error.message}`
    );
  }
});
```

## Key Points

### 1. **setCustomUserClaims** - THE CRITICAL LINE

```javascript
// This is what makes custom claims work in the app!
await admin.auth().setCustomUserClaims(uid, {
  admin: true,
  role: role,
  createdAt: new Date().toISOString()
});
```

The app looks specifically for the **"admin": true** claim.

### 2. **Dual-Layer Security**

```javascript
// Layer 1: Firebase Custom Claims (Server-verified)
await admin.auth().setCustomUserClaims(uid, { admin: true });

// Layer 2: Firestore Document (Backup + Metadata)
await admin.firestore().collection('users').doc(uid).set({
  role: 'ADMIN',
  ...
}, { merge: true });
```

This ensures:
- Primary check uses tamper-proof custom claims
- Fallback uses Firestore if claims unavailable
- Both stay in sync

### 3. **Admin Verification**

The function checks if the caller is already an admin:

```javascript
const callerClaims = context.auth.token;
if (!callerClaims.admin) {
  throw new functions.https.HttpsError('permission-denied', 'Only admins can create other admins');
}
```

This prevents regular users from creating admin accounts.

## Deployment Checklist

- [ ] Cloud Function has `setCustomUserClaims()` call
- [ ] Claims object includes `admin: true`
- [ ] Function also updates Firestore as fallback
- [ ] Caller verification is in place
- [ ] Error handling included
- [ ] Logs added for debugging
- [ ] Deploy to Firebase

## Testing the Custom Claims

After deploying, verify it's working:

```javascript
// In Firebase Console > Functions > Logs
// When creating admin, you should see:
// ✓ Custom claims set for admin user <uid>
// ✓ Firestore document created/updated for user <uid>

// In your app Logcat, when admin logs in:
// ✓ D/UserProfile: Using ADMIN role from Firebase custom claims
```

## Verification Steps

1. **Deploy the Cloud Function above** (or verify yours has `setCustomUserClaims`)
2. **Create/Update an admin user** via the AdminDashboard
3. **Check Firebase Console > Authentication > User**
   - Click on the admin user
   - Scroll down to "Custom claims"
   - Should see: `{ "admin": true, "role": "ADMIN", ... }`
4. **Log in as that admin**
   - Check Logcat
   - Should see: "Using ADMIN role from Firebase custom claims"
5. **Verify routing**
   - Should go to AdminDashboard, not UserDashboard

## Troubleshooting

### Problem: "Custom claims not available" message

**Solution**: The Cloud Function didn't call `setCustomUserClaims()`. Update your function to include:
```javascript
await admin.auth().setCustomUserClaims(uid, { admin: true });
```

### Problem: Admin still goes to UserDashboard

**Possible causes**:
1. Custom claim not set (check Function logs)
2. Need to logout and login again (to refresh token with claims)
3. Firestore `role` field is REGISTERED (fallback conflict)

**Solution**:
```javascript
// Ensure BOTH are set:
await admin.auth().setCustomUserClaims(uid, { admin: true });
await admin.firestore().collection('users').doc(uid).set({
  role: 'ADMIN'
}, { merge: true });
```

### Problem: "Only admins can create other admins" error

**This is expected!** Regular users cannot create admin accounts. You need:
1. Bootstrap: Create first admin manually in Firebase Console
2. Then: First admin can create other admins

To create the first admin manually:
1. Go to Firebase Console > Authentication
2. Click "Add user"
3. Create account with email/password
4. Go to that user's "Custom claims" section
5. Add: `{ "admin": true }`
6. Click "Update"

Now that user can log in as admin and create others.

## Security Implications

With this setup:
- ✅ Only Firebase can set/modify admin claims
- ✅ Client cannot fake admin status
- ✅ Firestore rules can check claims
- ✅ Logout required to revoke admin access
- ✅ Claims are token-bound (expire when token expires)

## Advanced: Firestore Security Rules with Custom Claims

Once custom claims are working, you can use them in security rules:

```javascript
// Restrict admin-only collections
match /admin_settings/{document=**} {
  allow read, write: if request.auth.token.admin == true;
}

// Restrict audit logs
match /audit_logs/{document=**} {
  allow read: if request.auth.token.admin == true;
  allow write: if false; // Only Cloud Functions
}

// User can only read their own profile, admins can read all
match /users/{uid} {
  allow read: if request.auth.uid == uid || request.auth.token.admin == true;
  allow write: if request.auth.uid == uid;
  allow delete: if request.auth.token.admin == true;
}
```

## Related Documentation

- [Firebase Custom Claims](https://firebase.google.com/docs/auth/admin-setup-v8#set_custom_user_claims_via_the_admin_sdk)
- [Firebase Security Rules](https://firebase.google.com/docs/rules)
- [Android Callable Cloud Functions](https://firebase.google.com/docs/functions/callable)

## Status

🟢 **App Ready**: App code is ready to use custom claims
⚠️ **Cloud Function**: Verify your function includes `setCustomUserClaims()`
⚠️ **First Admin**: May need to create manually in Firebase Console
