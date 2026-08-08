# FoodFusion AI

Modern AI-ready food delivery application

## Tech Stack
| Category       | Technology |
| -------------- | ---------- |
| Language       | Kotlin     |
| Architecture   | MVVM, Single Activity |
| UI Framework   | ViewBinding, XML Layouts |
| Dependency Inj.| Hilt |
| Networking     | Retrofit |
| Database       | Room |
| Backend        | Firebase |
| Asynchrony     | Coroutines, Flow |
| Image Loading  | Coil |

## Architecture
```text
Presentation Layer (UI, ViewModels)
       ↓
Domain Layer (Use Cases)
       ↓
Data Layer (Repositories, Data Sources)
```

## Package Structure
```text
com.foodfusionai.app
├── data
├── di
├── domain
├── ui
└── utils
```

## Setup Instructions
1. Clone the repository
2. Open in Android Studio
3. Create `local.properties` and configure required keys
4. Add `google-services.json` to the `app/` directory
5. Sync project with Gradle files

## Build Commands
```bash
./gradlew build
./gradlew assembleDebug
./gradlew bundleRelease
```

## Phase Roadmap
1. Project Setup
2. Dependency Injection
3. Navigation
4. Authentication
5. Home Screen
6. Food Catalog
7. Cart Management
8. Checkout Process
9. Order Tracking
10. User Profile
11. Search and Filtering
12. Notifications
13. Favorites
14. Reviews and Ratings
15. Restaurant Dashboard
16. Driver App Integration
17. Analytics
18. AI Recommendations
19. Dark Mode Refinement
20. Final Polish & Release

## License
MIT License
