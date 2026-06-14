# MediConnect — Native Android App

Native Android appointment booking app for a medical consultancy firm. Built with Kotlin + Jetpack Compose + Material 3.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Navigation:** Navigation Compose
- **Networking:** Ktor Client
- **Serialization:** Kotlinx Serialization
- **Architecture:** MVVM (planned)

## Setup

1. Open in **Android Studio Ladybug (2024.3)** or newer
2. Let Gradle sync complete
3. Run on emulator or device (API already configured for `https://mediconnect.nma-it.com/api/`)

> 💡 The API is live at `https://mediconnect.nma-it.com`. Health check: [https://mediconnect.nma-it.com/api/health](https://mediconnect.nma-it.com/api/health)

## Project Structure

```
app/src/main/java/com/mediconnect/
├── MediConnectApp.kt          # Application class
├── MainActivity.kt            # Entry activity
├── navigation/
│   ├── Screen.kt              # Route definitions
│   └── NavGraph.kt            # Navigation graph
├── ui/
│   ├── theme/
│   │   ├── Color.kt           # Color palette
│   │   └── Theme.kt           # Material 3 theme
│   └── screens/
│       ├── LoginScreen.kt
│       ├── RegisterScreen.kt
│       ├── HomeScreen.kt
│       ├── DoctorsScreen.kt
│       ├── DoctorDetailScreen.kt
│       ├── BookingScreen.kt
│       ├── AppointmentsScreen.kt
│       └── ProfileScreen.kt
└── data/
    ├── api/
    │   └── MediConnectApi.kt   # Ktor API client
    └── model/
        └── Models.kt           # Data classes
```

## Screens

| Screen | Route |
|---|---|
| Login | `login` |
| Register | `register` |
| Home | `home` |
| Doctors List | `doctors` |
| Doctor Detail | `doctors/{doctorId}` |
| Booking | `booking/{doctorId}/{date}/{startTime}` |
| My Appointments | `appointments` |
| Profile | `profile` |
