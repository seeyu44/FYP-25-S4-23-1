// Backend API Configuration
const API_BASE = "https://dg-backend-2j05.onrender.com";

// API Helper function
async function apiCall(endpoint, method = "GET", body = null) {
  const options = {
    method,
    headers: {
      "Content-Type": "application/json",
    }
  };

  // Add authorization token if it exists
  const token = localStorage.getItem("access_token");
  if (token) {
    options.headers["Authorization"] = `Bearer ${token}`;
  }

  if (body) {
    options.body = JSON.stringify(body);
  }

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, options);
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.detail || `API Error: ${response.status}`);
    }
    return await response.json();
  } catch (error) {
    console.error("API Error:", error);
    throw error;
  }
}

// Auth Functions
async function register(username, email, password) {
  return apiCall("/auth/register", "POST", { username, email, password });
}

async function verifyOtp(email, otp) {
  return apiCall("/auth/verify-otp", "POST", { email, otp });
}

async function resendOtp(email) {
  return apiCall("/auth/resend-otp", "POST", { email });
}

async function login(email, password) {
  const data = await apiCall("/auth/login", "POST", { email, password });
  // Save tokens to localStorage
  localStorage.setItem("access_token", data.access_token);
  localStorage.setItem("refresh_token", data.refresh_token);
  return data;
}

async function refreshToken() {
  const refreshToken = localStorage.getItem("refresh_token");
  if (!refreshToken) throw new Error("No refresh token");
  const data = await apiCall("/auth/refresh", "POST", { refresh_token: refreshToken });
  localStorage.setItem("access_token", data.access_token);
  return data;
}

async function logout() {
  localStorage.removeItem("access_token");
  localStorage.removeItem("refresh_token");
}

// User Functions
async function getCurrentUser() {
  return apiCall("/users/me", "GET");
}
