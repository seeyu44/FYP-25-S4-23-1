# App Review Feature

This feature allows users to submit reviews for the application directly from the dashboard.

## Components Created

### 1. Entity Model
- **File**: [entity/domain/entities/AppReview.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/domain/entities/AppReview.kt)
- **Purpose**: Defines the review data structure with validation
- **Fields**:
  - `id`: Unique identifier (Firebase document ID)
  - `userId`: User who submitted the review
  - `rating`: 1-5 star rating (validated)
  - `description`: Review text (required)
  - `createdAt`: Unix timestamp (seconds) when review was created
  - `updatedAt`: Unix timestamp (seconds) when review was last updated

### 2. Firebase Repository
- **File**: [data/remote/firebase/ReviewRepository.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/ReviewRepository.kt)
- **Purpose**: Handles Firebase Firestore operations for reviews
- **Methods**:
  - `submitReview()`: Submit a new review
  - `updateReview()`: Update an existing review
  - `getUserReviews()`: Get all reviews from a specific user
  - `getAllReviews()`: Get all reviews (admin only)
  - `deleteReview()`: Delete a review

### 3. Review Dialog UI
- **File**: [boundary/dashboard/ReviewDialog.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/dashboard/ReviewDialog.kt)
- **Purpose**: Provides a Material 3 dialog for collecting user reviews
- **Features**:
  - Interactive 5-star rating selector
  - Multi-line text field for review description
  - Form validation (ensures both rating and description are provided)
  - Cancel and Submit actions

### 4. Integration Points
- **ViewModel** ([control/viewmodel/AppMainViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/control/viewmodel/AppMainViewModel.kt)):
  - Added `ReviewRepository` instance
  - Added `submitReview()` method that handles the submission flow
  - Shows success/error messages via UI state

- **MainActivity** ([MainActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/MainActivity.kt)):
  - Passes `viewModel::submitReview` callback to UserDashboard

- **UserDashboard** ([boundary/dashboard/UserDashboard.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/dashboard/UserDashboard.kt)):
  - Added "Leave a Review" menu item in the top bar dropdown
  - Shows ReviewDialog when menu item is clicked
  - Handles review submission and dialog dismissal

## Firebase Structure

Reviews are stored in Firestore at: `reviews/{reviewId}`

Document structure:
```json
{
  "userId": "string",
  "rating": "number (1-5)",
  "description": "string",
  "createdAt": "number (unix timestamp in seconds)",
  "updatedAt": "number (unix timestamp in seconds)"
}
```

## Usage

1. Users can access the review feature from the dashboard
2. Click the three-dot menu (⋮) in the top-right corner
3. Select "Leave a Review"
4. A dialog appears with:
   - 5-star rating selector
   - Text field for detailed feedback
5. Submit the review
6. Success message is displayed
7. Review is stored in Firebase Firestore

## Security Considerations

- Reviews are associated with the user's Firebase UID
- All Firebase operations use async/await for proper error handling
- Form validation ensures data quality before submission
- Consider adding Firestore security rules to:
  - Prevent users from editing others' reviews
  - Rate limit review submissions
  - Validate data on the server side
