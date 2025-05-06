# VarioWatch Kotlin Style Guide and Best Practices

## App goals / functionality

- Goal is to have an app that works as a variometer tool for aiding paragliding and hang gliding flights by indicating vertical speed on screen and by sound
- Specifically targeting Android Wear OS app (Galaxy Watch 6)
- App has 2 modes, running and stopped. While running, a background service will report barometric pressure changes, that will be used to calculate altitude and vertical speed
- Altitude, vertical speed and pressure should be reported in the main screen
- Vertical speed is also used to generate intermittent synthetic sound which changes in frequency and duration based on vertical acceleration
- A Noise reduction filter should be applied to the background service to reduce noise and drastic oscillations (caused by noise or shock waves, other than actual variation in altitude) on the sensor readings
- Altitude given pressure is calculated using hypsometric equation and using QNH stored in user preferences (which defaults to standard atmospheric pressure if no altitude calibration)
- When calibrating the Altitude, the user will input the known altitude of the take off and given pressure read from take off, app should calculate and save QNH in user preferences. That adjusted value will then be used to calculate subsequent altitudes based on pressure reading
- The app will have a SoundSynthetizer service that will receive vertical speed variations and generate sound on frequencies and durations that can change as the vertical speed changes, based on some user defined constants
- Every code refactor / implementation iteration is done by AI, it should check for compilation errors and for not breaking current functionality

## Code Style and Formatting

### General Guidelines
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations
- Remove unused imports
- Organize imports in the following order:
  1. Android/Kotlin imports
  2. Third-party libraries
  3. Project imports
- Keep code consistent and focus on readability and maintainability

### Naming Conventions
- Class names: PascalCase (e.g., `BarometricSensor`)
- Functions/Variables: camelCase (e.g., `calculateVerticalSpeed`)
- Constants: SCREAMING_SNAKE_CASE (e.g., `MAX_PRESSURE_SAMPLES`)
- Private properties: camelCase with no underscores
- Interface names: No 'I' prefix, use PascalCase
- Test classes: Add 'Test' suffix (e.g., `VerticalSpeedCalculatorTest`)

## Kotlin-Specific Guidelines

### Null Safety
- Prefer non-nullable types whenever possible
- Use `?.` safe call operator instead of null checks where appropriate
- Use `!!` operator only in tests or when absolutely certain about non-null value
- Consider using `requireNotNull()` for early validation

### Coroutines
- Use structured concurrency principles
- Always provide a CoroutineScope for launching coroutines
- Use appropriate dispatchers:
  - `Dispatchers.Default` for CPU-intensive tasks
  - `Dispatchers.IO` for I/O operations
  - `Dispatchers.Main` for UI updates
- Handle exceptions using `CoroutineExceptionHandler`

### State Management
- Use `StateFlow` for UI state management
- Prefer immutable state using `data class`
- Use sealed classes/interfaces for handling UI events

### Smart Cast and Type Safety
- Leverage Kotlin's smart cast feature
- Use `when` expressions over if-else chains
- Make `when` expressions exhaustive

## Wear OS Specific Guidelines

### Performance
- Minimize object allocations in performance-critical paths
- Use `@Composable` functions efficiently
- Implement proper cleanup in `onPause()` and `onDestroy()`
- Cache sensor readings appropriately

### Battery Optimization
- Use appropriate sensor sampling rates
- Implement proper wake locks
- Clean up resources when app goes to background
- Use WorkManager for background tasks

### UI/UX Guidelines
- Follow Material Design for Wear OS
- Target round watch faces only (optimized for Samsung Galaxy Watch 6)
- Design UI elements with circular screen layout in mind
- Ensure touch targets are at least 48x48dp
- Use clear, high-contrast colors for outdoor visibility
- Utilize the rotating bezel for navigation when available
- Design complications that work well on round displays

## Testing

### Unit Tests
- Test each component in isolation
- Use descriptive test names following: `given_when_then` pattern
- Mock dependencies using Mockito or MockK
- Test edge cases and error conditions

### Integration Tests
- Test sensor data processing pipeline
- Verify audio feedback generation
- Test UI state management
- Verify proper cleanup of resources

## Architecture

### Clean Architecture
- Separate concerns into data, domain, and presentation layers
- Use dependency injection (preferably Hilt)
- Follow SOLID principles
- Implement Repository pattern for data operations

### Compose UI
- Use single source of truth for state
- Keep composables small and focused
- Use proper layout hierarchy
- Implement proper recomposition optimization

## Documentation

### Code Documentation
- Document public APIs
- Include usage examples in complex functions
- Document non-obvious implementation details
- Include links to relevant resources or specifications

## Git Workflow

### Commit Messages
- Use conventional commits format
- Include ticket number if applicable
- Keep commits focused and atomic

### Branch Strategy
- main: Stable production code
- develop: Integration branch
- feature/*: New features
- bugfix/*: Bug fixes
- release/*: Release preparation

## Code Review Guidelines

### Review Checklist
- Verify null safety handling
- Check resource cleanup
- Ensure proper error handling
- Verify battery optimization
- Check UI responsiveness
- Validate sensor usage
- Review documentation

### Performance Considerations
- Review sensor sampling rates
- Check memory allocations
- Verify background operation
- Review battery impact

## Build System

### Gradle Commands
- Always use `--no-daemon` flag with gradlew commands to avoid file locking issues
- Example: `./gradlew clean --no-daemon`, `./gradlew build --no-daemon`

Remember to update these guidelines as the project evolves and new best practices emerge.