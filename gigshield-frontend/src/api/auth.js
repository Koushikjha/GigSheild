import { client } from './client'

// Mirrors com.gigshield.auth.controller.AuthController exactly:
// phone + OTP is the *only* credential in this system — there is no
// email/password anywhere in the backend.

export function sendOtp(phone) {
  return client.post('/api/v1/auth/send-otp', { phone }).then((response) => response.data)
}

// `registration` is only required the first time a phone number is seen —
// the backend infers "new user" vs. "login" from whether these fields are
// present (see AuthService.buildRegistrationData). Passing them for an
// existing user is harmless; the backend just ignores them.
export function verifyOtp(phone, otp, registration = {}) {
  return client
    .post('/api/v1/auth/verify-otp', {
      phone,
      otp,
      ...registration,
    })
    .then((response) => response.data)
}

export function refresh(refreshToken) {
  return client.post('/api/v1/auth/refresh', { refreshToken }).then((response) => response.data)
}

export function logout() {
  return client.post('/api/v1/auth/logout')
}
