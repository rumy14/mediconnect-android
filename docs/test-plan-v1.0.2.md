# MediConnect App - SQA Test Plan v1.0.2

**App:** MediConnect Android (Kotlin + Jetpack Compose + Ktor + Material 3)  
**Version Under Test:** 1.0.2  
**API Base:** `https://mediconnect.nma-it.com/api/`  
**Health Check:** `https://mediconnect.nma-it.com/api/health`  
**APK:** `https://ai.nma-it.com/mediconnect.apk` (latest)  
**Tester:** NMA (SQA Agent)  
**Date:** 2026-06-19  

---

## 1. Test Scope

### In Scope
- All 8 screens: Login, Register, Home, Doctors, DoctorDetail, Booking, Appointments, Profile
- API integration (authentication, data fetch, mutations)
- UI/UX consistency (Material 3)
- Error handling & edge cases
- Performance baseline

### Out of Scope
- Backend API unit tests (assumed tested separately)
- Security penetration testing (separate audit)
- iOS version

---

## 2. Test Environment

| Component | Details |
|-----------|---------|
| Device | Android emulator + physical device |
| OS Versions | Android 12, 13, 14 |
| Screen Sizes | Phone (6.1"), Tablet (10") |
| Network | WiFi, 4G, offline mode |
| Build | Release APK from `https://ai.nma-it.com/mediconnect.apk` |

---

## 3. Test Cases by Screen

### 3.1 Authentication (Login & Register)

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| AUTH-001 | Valid login | Enter valid credentials → tap Login | Home screen loads, token stored | P1 |
| AUTH-002 | Invalid password | Wrong password → tap Login | Error message, stay on login | P1 |
| AUTH-003 | Empty fields | Tap Login with empty fields | Validation errors displayed | P1 |
| AUTH-004 | Invalid email format | Enter "abc" as email → tap Login | Email format validation error | P2 |
| AUTH-005 | Register new account | Fill form → tap Register | Success message, redirect to login | P1 |
| AUTH-006 | Register duplicate email | Use existing email | Error: email already exists | P1 |
| AUTH-007 | Password mismatch | Different passwords in confirm field | Validation error | P2 |
| AUTH-008 | Token expiry | Wait for token expiry → navigate | Redirect to login, no crash | P1 |
| AUTH-009 | Biometric login (if supported) | Enable biometric → authenticate | Quick login without credentials | P3 |
| AUTH-010 | Network error during login | Airplane mode → tap Login | Network error message, retry option | P1 |

### 3.2 Home Screen

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| HOME-001 | Load home data | Open app → login → home | Categories, featured doctors, banners load | P1 |
| HOME-002 | Pull to refresh | Swipe down | Data refreshes, loading indicator | P2 |
| HOME-003 | Navigate to doctors | Tap "See All Doctors" | Doctors list screen opens | P1 |
| HOME-004 | Quick book from home | Tap featured doctor → Book | Booking screen with doctor pre-selected | P1 |
| HOME-005 | Empty state (no data) | If API returns empty | "No data available" message shown | P2 |
| HOME-006 | Network error | API down → open home | Error state with retry button | P1 |
| HOME-007 | Scroll performance | Scroll through long lists | Smooth 60fps, no jank | P2 |

### 3.3 Doctors List

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| DOC-001 | Load doctors list | Navigate to Doctors | List loads with doctor cards | P1 |
| DOC-002 | Search by name | Type "Dr. Smith" in search | Filtered results shown | P1 |
| DOC-003 | Search no results | Type "xyz123" | "No doctors found" message | P2 |
| DOC-004 | Filter by specialty | Tap filter → select "Cardiology" | Only cardiologists shown | P1 |
| DOC-005 | Filter by availability | Toggle "Available Today" | Only available doctors shown | P2 |
| DOC-006 | Sort by rating | Sort by "Highest Rated" | Doctors sorted by rating desc | P2 |
| DOC-007 | Pagination | Scroll to bottom | More doctors load (infinite scroll) | P2 |
| DOC-008 | Tap doctor card | Tap any doctor | DoctorDetail screen opens | P1 |
| DOC-009 | Clear search/filter | Tap clear button | Full list restored | P2 |
| DOC-010 | Offline mode | No network → open doctors | Cached data or error state | P1 |

### 3.4 Doctor Detail

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| DET-001 | Load doctor info | Tap doctor from list | Photo, name, specialty, bio, rating, reviews load | P1 |
| DET-002 | View availability | Scroll to calendar | Available slots shown for next 7 days | P1 |
| DET-003 | Tap book appointment | Tap "Book Appointment" | Booking screen with doctor pre-filled | P1 |
| DET-004 | View reviews | Scroll to reviews section | Patient reviews with ratings | P2 |
| DET-005 | Call doctor (if feature) | Tap phone icon | Phone dialer opens with number | P3 |
| DET-006 | Share doctor profile | Tap share icon | Share sheet opens | P3 |
| DET-007 | Back navigation | Tap back arrow | Returns to doctors list | P1 |
| DET-008 | Doctor not found | Invalid doctor ID in deep link | Error: doctor not found | P2 |

### 3.5 Booking

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| BOOK-001 | Select date | Tap date from calendar | Date highlighted, slots update | P1 |
| BOOK-002 | Select time slot | Tap available time slot | Slot selected, confirmation enabled | P1 |
| BOOK-003 | Book with reason | Enter reason → tap Confirm | Booking confirmed, success message | P1 |
| BOOK-004 | Book without reason | Leave reason empty → tap Confirm | Validation: reason required | P2 |
| BOOK-005 | Double booking prevention | Try booking same slot twice | Second booking rejected | P1 |
| BOOK-006 | Slot taken in real-time | Slot taken while viewing | Error on confirm: slot unavailable | P1 |
| BOOK-007 | Past date selection | Try selecting yesterday | Past dates disabled | P2 |
| BOOK-008 | Cancel booking | After booking → tap Cancel | Confirmation dialog → cancelled | P1 |
| BOOK-009 | Reschedule | Tap Reschedule → select new slot | New slot confirmed, old released | P2 |
| BOOK-010 | Offline booking | No network → tap Confirm | Queue for sync or error | P1 |

### 3.6 Appointments

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| APT-001 | View appointments list | Navigate to Appointments | Upcoming and past appointments shown | P1 |
| APT-002 | Upcoming filter | Toggle "Upcoming" | Only future appointments | P2 |
| APT-003 | Past filter | Toggle "Past" | Only past appointments | P2 |
| APT-004 | Cancel upcoming | Tap Cancel on upcoming apt | Confirmation → removed from list | P1 |
| APT-005 | View appointment details | Tap appointment card | Detail screen with all info | P1 |
| APT-006 | Rebook from past | Tap "Book Again" on past apt | Booking screen with same doctor | P2 |
| APT-007 | Empty state | No appointments | "No appointments" illustration | P2 |
| APT-008 | Real-time status update | Doctor cancels → refresh | Status updated to "Cancelled" | P1 |
| APT-009 | Appointment reminder | 1 hour before appointment | Push notification received | P2 |
| APT-010 | Swipe to delete | Swipe left on past apt | Delete confirmation → removed | P3 |

### 3.7 Profile

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| PROF-001 | View profile | Navigate to Profile | User info, photo, settings displayed | P1 |
| PROF-002 | Edit profile | Tap Edit → change name → Save | Name updated, success message | P1 |
| PROF-003 | Upload photo | Tap photo → select from gallery | Photo uploaded and displayed | P2 |
| PROF-004 | Change password | Tap Change Password → enter old/new | Password updated, re-login required | P1 |
| PROF-005 | Logout | Tap Logout → confirm | Redirect to login, token cleared | P1 |
| PROF-006 | Delete account | Tap Delete Account → confirm | Account deleted, data removed | P1 |
| PROF-007 | Notification settings | Toggle notifications | Preference saved, toggle reflects state | P2 |
| PROF-008 | Dark mode toggle | Toggle dark mode | App theme changes immediately | P2 |
| PROF-009 | Invalid phone format | Enter "abc" as phone | Validation error | P2 |
| PROF-010 | Profile data persistence | Kill app → reopen | Profile data still loaded | P1 |

---

## 4. API Testing

### 4.1 Endpoints to Test

| Endpoint | Method | Test Focus |
|----------|--------|------------|
| `/api/health` | GET | Server availability |
| `/api/auth/login` | POST | Valid/invalid credentials, token format |
| `/api/auth/register` | POST | New user, duplicate, validation |
| `/api/auth/refresh` | POST | Token refresh flow |
| `/api/doctors` | GET | List, search, filter, pagination |
| `/api/doctors/{id}` | GET | Valid ID, invalid ID, 404 |
| `/api/doctors/{id}/slots` | GET | Available slots, date range |
| `/api/appointments` | GET | List, filters, status |
| `/api/appointments` | POST | Create, validation, conflicts |
| `/api/appointments/{id}` | PUT | Update, reschedule |
| `/api/appointments/{id}` | DELETE | Cancel, already cancelled |
| `/api/user/profile` | GET | Authenticated, unauthenticated |
| `/api/user/profile` | PUT | Update, validation |
| `/api/user/avatar` | POST | Upload, size limits, formats |

### 4.2 API Error Scenarios

| ID | Scenario | Expected Response |
|----|----------|-------------------|
| API-001 | 401 Unauthorized | Redirect to login |
| API-002 | 403 Forbidden | Error message, no crash |
| API-003 | 404 Not Found | User-friendly error |
| API-004 | 500 Server Error | Retry option, error logged |
| API-005 | Timeout (>30s) | Timeout message, retry |
| API-006 | Malformed JSON | Parse error handled |
| API-007 | Empty response body | Handled gracefully |
| API-008 | SSL certificate error | Security warning |

---

## 5. UI/UX Testing

| ID | Test Case | Criteria |
|----|-----------|----------|
| UI-001 | Material 3 compliance | Cards, buttons, typography match spec |
| UI-002 | Dark mode | All screens readable, no hardcoded colors |
| UI-003 | Accessibility | TalkBack labels, minimum touch 48dp |
| UI-004 | Screen rotation | No layout breaks on rotation |
| UI-005 | Tablet layout | Proper use of extra space |
| UI-006 | Loading states | Skeletons or spinners during load |
| UI-007 | Error states | Empty/error illustrations, retry buttons |
| UI-008 | Typography | No truncation, readable sizes |
| UI-009 | Colors | Brand colors consistent, good contrast |
| UI-010 | Animations | Smooth transitions, no jank |

---

## 6. Performance Testing

| ID | Metric | Target | How to Test |
|----|--------|--------|-------------|
| PERF-001 | Cold start | <3 seconds | Launch from killed state |
| PERF-002 | Screen load | <1 second | Navigation between screens |
| PERF-003 | API response | <2 seconds | Measure from request to render |
| PERF-004 | List scroll | 60fps | GPU profiling |
| PERF-005 | Memory usage | <200MB | Android Studio profiler |
| PERF-006 | APK size | <50MB | Check file size |
| PERF-007 | Battery drain | <5%/hour idle | Background monitoring |
| PERF-008 | Image load | <1 second | Doctor photos, avatars |

---

## 7. Regression Checklist (Per Build)

- [ ] Login with valid credentials
- [ ] Register new account
- [ ] View home screen
- [ ] Search doctors
- [ ] Book appointment
- [ ] View appointments
- [ ] Cancel appointment
- [ ] Edit profile
- [ ] Logout
- [ ] Login again
- [ ] Deep link to doctor
- [ ] Offline error handling

---

## 8. Bug Reporting Template

```
**ID:** BUG-XXX
**Title:** [Brief description]
**Severity:** Critical/High/Medium/Low
**Priority:** P1/P2/P3
**Screen:** [Login/Register/Home/Doctors/DoctorDetail/Booking/Appointments/Profile]
**Environment:** [Android version, device, build]

**Steps to Reproduce:**
1. Step 1
2. Step 2
3. Step 3

**Expected Result:**
[What should happen]

**Actual Result:**
[What actually happens]

**Screenshots/Videos:**
[Attach media]

**Logs:**
```
[Logcat output]
```

**API Request/Response (if applicable):**
```
Request: [curl command]
Response: [JSON]
```
```

---

## 9. Sign-Off Criteria

- All P1 test cases pass
- No Critical or High severity bugs open
- Performance targets met
- API error handling verified
- UI/UX checklist complete
- Regression checklist complete

---

## 10. Tools & Commands

```bash
# Download latest APK
curl -o mediconnect.apk https://ai.nma-it.com/mediconnect.apk

# Check API health
curl https://mediconnect.nma-it.com/api/health

# Test API endpoints
curl -X POST https://mediconnect.nma-it.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}'

# Install APK
adb install mediconnect.apk

# Capture logs
adb logcat | grep mediconnect

# Screen recording
adb shell screenrecord /sdcard/test.mp4
```

---

**Next Steps:**
1. Execute test cases by priority (P1 first)
2. Log all bugs with the template above
3. Re-test after fixes
4. Update this plan with new test cases as features are added

*Test Plan Version: 1.0.2*  
*Last Updated: 2026-06-19*
