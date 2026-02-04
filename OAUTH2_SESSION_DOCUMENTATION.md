# OAuth2 and Session Management Documentation

## Overview

This document describes the OAuth2 social login implementation with robust server-side session management in the Spring PetClinic REST API. The implementation provides Google OAuth2 authentication with comprehensive session attribute management.

## Features

- **Google OAuth2 Social Login**: Secure authentication using Google OAuth2
- **Server-side Session Management**: JDBC-based session storage with Spring Session
- **Session Attributes API**: Full CRUD operations for session attributes
- **Role-based Access Control**: Configurable admin roles based on email addresses
- **User Preferences**: Persistent user preferences stored in session
- **Session Security**: Protection against session fixation and unauthorized access

## Architecture

### Components

1. **OAuth2AuthenticationSuccessHandler**: Handles successful OAuth2 authentication
2. **SessionManagementService**: Manages session attributes and user data
3. **AuthRestController**: Authentication endpoints
4. **SessionRestController**: Session management endpoints
5. **User Model**: Enhanced with OAuth2 fields (oauth_provider, oauth_id, picture_url)

### Database Schema

The implementation uses the following enhanced user schema:

```sql
CREATE TABLE users (
  username VARCHAR(20) NOT NULL PRIMARY KEY,
  password VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  email VARCHAR(100),
  first_name VARCHAR(50),
  last_name VARCHAR(50),
  oauth_provider VARCHAR(20),
  oauth_id VARCHAR(100),
  picture_url VARCHAR(255)
);
```

Spring Session tables are automatically created for session storage.

## Configuration

### Application Properties

```properties
# Enable OAuth2 authentication
petclinic.security.enable=true
petclinic.security.oauth2.enable=true

# Admin email configuration for role assignment
petclinic.security.admin-emails=admin@petclinic.com,admin@example.com

# Session timeout in seconds (default: 1800 = 30 minutes)
petclinic.security.session-timeout=1800

# Google OAuth2 Client Configuration
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.google.scope=openid,profile,email
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}

# Spring Session JDBC Storage
spring.session.store-type=jdbc
spring.session.jdbc.cleanup-cron=0 */1 * * * *
spring.session.jdbc.initialize-schema=always
```

### Google OAuth2 Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable Google+ API
4. Create OAuth2 credentials
5. Add authorized redirect URIs:
   - `http://localhost:9966/petclinic/login/oauth2/code/google` (for local development)
   - `https://yourdomain.com/petclinic/login/oauth2/code/google` (for production)

## API Endpoints

### Authentication APIs

#### GET /api/auth/login
Initiate OAuth2 login flow.

**Response:**
```json
{
  "authenticated": false,
  "message": "Redirect to OAuth2 provider",
  "loginUrl": "/oauth2/authorization/google"
}
```

#### GET /api/auth/status
Get current authentication status.

**Response (Authenticated):**
```json
{
  "authenticated": true,
  "user": {
    "username": "user@example.com",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "pictureUrl": "https://example.com/picture.jpg",
    "enabled": true,
    "oauthProvider": "google",
    "roles": ["ROLE_OWNER_ADMIN"]
  },
  "sessionInfo": {
    "sessionId": "session-id",
    "createdTime": "2024-01-01T10:00:00",
    "lastActivityTime": "2024-01-01T10:30:00",
    "maxInactiveInterval": 1800,
    "isNew": false,
    "authenticated": true,
    "username": "user@example.com",
    "email": "user@example.com"
  }
}
```

#### POST /api/auth/logout
Logout and invalidate session.

**Response:**
```json
{
  "success": true,
  "message": "Successfully logged out"
}
```

### Session Management APIs

#### GET /api/session/info
Get full session information.

**Response:**
```json
{
  "sessionInfo": {
    "sessionId": "session-id",
    "createdTime": "2024-01-01T10:00:00",
    "lastActivityTime": "2024-01-01T10:30:00",
    "maxInactiveInterval": 1800,
    "isNew": false,
    "authenticated": true,
    "username": "user@example.com",
    "email": "user@example.com"
  }
}
```

#### GET /api/session/user
Get authenticated user information.

**Response:**
```json
{
  "user": {
    "username": "user@example.com",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "pictureUrl": "https://example.com/picture.jpg",
    "enabled": true,
    "oauthProvider": "google",
    "roles": ["ROLE_OWNER_ADMIN"]
  }
}
```

#### GET /api/session/attributes
Get all session attributes.

**Response:**
```json
{
  "authenticated_user": { /* User object */ },
  "session_created_time": "2024-01-01T10:00:00",
  "last_activity_time": "2024-01-01T10:30:00",
  "user_preferences": {
    "theme": "light",
    "language": "en",
    "timezone": "UTC",
    "notifications": true
  },
  "custom_attribute": "custom_value"
}
```

#### GET /api/session/attributes/{key}
Get specific session attribute.

**Response:**
```json
{
  "key": "theme",
  "value": "dark"
}
```

#### PUT /api/session/attributes/{key}
Set/update session attribute.

**Request Body:**
```json
{
  "value": "dark"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Session attribute set successfully",
  "key": "theme",
  "value": "dark"
}
```

#### DELETE /api/session/attributes/{key}
Remove session attribute.

**Response:**
```json
{
  "success": true,
  "message": "Session attribute removed successfully",
  "key": "theme"
}
```

## Session Attributes

### System Attributes (Protected)

These attributes are automatically managed and cannot be modified via API:

- `authenticated_user`: The authenticated user object
- `session_created_time`: Session creation timestamp
- `last_activity_time`: Last activity timestamp
- `SPRING_SECURITY_*`: Spring Security internal attributes

### User Preferences (Default)

Default user preferences are automatically initialized:

```json
{
  "theme": "light",
  "language": "en",
  "timezone": "UTC",
  "notifications": true
}
```

### Custom Attributes

Users can store any custom attributes in their session:

- User interface preferences
- Application settings
- Temporary data
- Custom workflow state

## Role Assignment

### Default Role
All authenticated users receive the `ROLE_OWNER_ADMIN` role by default.

### Admin Roles
Users with emails listed in `petclinic.security.admin-emails` receive additional roles:
- `ROLE_ADMIN`
- `ROLE_VET_ADMIN`
- `ROLE_OWNER_ADMIN`

### Configuration Example
```properties
petclinic.security.admin-emails=admin@petclinic.com,superuser@example.com,manager@petclinic.org
```

## Security Features

### Session Security
- **Session Fixation Protection**: Sessions are migrated on authentication
- **Session Timeout**: Configurable timeout (default 30 minutes)
- **Concurrent Session Control**: Maximum 1 session per user
- **CSRF Protection**: Disabled for REST API usage

### Attribute Protection
- System attributes cannot be modified or deleted
- Authentication required for all session endpoints
- Proper error handling for unauthorized access

### OAuth2 Security
- Secure token exchange with Google
- User information validation
- Automatic user creation and linking

## Error Handling

### Common Error Responses

#### 401 Unauthorized
```json
{
  "error": "User not authenticated"
}
```

#### 403 Forbidden
```json
{
  "error": "Cannot modify system attribute: authenticated_user"
}
```

#### Session Expired
```json
{
  "error": "SESSION_EXPIRED",
  "message": "Session expired. Please login again."
}
```

## Testing

### Integration Tests

The implementation includes comprehensive integration tests:

1. **OAuth2IntegrationTests**: Tests OAuth2 login flow and session persistence
2. **SessionAttributeManagementTests**: Tests session attribute CRUD operations

### Test Configuration

Tests use H2 in-memory database with test OAuth2 configuration:

```properties
petclinic.security.enable=true
petclinic.security.oauth2.enable=true
spring.security.oauth2.client.registration.google.client-id=test-client-id
spring.security.oauth2.client.registration.google.client-secret=test-client-secret
```

## Distributed Session Storage

### Redis Configuration (Optional)

For distributed deployments, configure Redis session storage:

```properties
spring.session.store-type=redis
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=your-password
```

### MongoDB Configuration (Optional)

For MongoDB session storage:

```properties
spring.session.store-type=mongodb
spring.data.mongodb.uri=mongodb://localhost:27017/petclinic
```

## Monitoring and Maintenance

### Session Cleanup

Spring Session automatically cleans up expired sessions based on the configured cron expression:

```properties
spring.session.jdbc.cleanup-cron=0 */1 * * * *
```

### Monitoring Endpoints

Use Spring Boot Actuator for monitoring:

```properties
management.endpoints.web.exposure.include=health,sessions,metrics
```

### Logging

Enable debug logging for troubleshooting:

```properties
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.session=DEBUG
```

## Migration Guide

### Adding OAuth2 to Existing Application

1. Add OAuth2 dependencies to `pom.xml`
2. Update database schema with OAuth2 fields
3. Configure OAuth2 properties
4. Update security configuration
5. Test authentication flow

### Migrating from Basic Auth

1. Keep existing user accounts
2. Link OAuth2 accounts to existing users by email
3. Gradually migrate users to OAuth2
4. Maintain backward compatibility during transition

## Troubleshooting

### Common Issues

1. **OAuth2 Redirect URI Mismatch**
   - Verify redirect URI in Google Console matches application configuration

2. **Session Not Persisting**
   - Check database connectivity
   - Verify Spring Session tables are created
   - Check session timeout configuration

3. **Role Assignment Not Working**
   - Verify admin email configuration
   - Check user email matches exactly (case-sensitive)

4. **CORS Issues**
   - Configure CORS for your frontend domain
   - Ensure credentials are included in requests

### Debug Steps

1. Enable debug logging
2. Check database session tables
3. Verify OAuth2 token exchange
4. Test with curl or Postman
5. Review browser network requests

## Best Practices

1. **Security**
   - Use HTTPS in production
   - Regularly rotate OAuth2 secrets
   - Monitor session usage
   - Implement proper CORS policies

2. **Performance**
   - Configure appropriate session timeout
   - Use connection pooling for database
   - Monitor session storage size
   - Clean up expired sessions regularly

3. **Scalability**
   - Use Redis for distributed sessions
   - Implement session replication
   - Monitor memory usage
   - Consider session clustering

4. **Maintenance**
   - Regular security updates
   - Monitor OAuth2 provider changes
   - Backup session data
   - Test disaster recovery procedures