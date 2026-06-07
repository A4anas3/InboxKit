// Resolved API URL from environment variables or local fallback
export const API_BASE_URL = 
  (typeof process !== 'undefined' && process?.env?.REACT_APP_API_URL) || 
  import.meta.env.VITE_API_URL || 
  'http://localhost:8080';

// Simple fetch-based api client
export const api = {
  async post(path, body, headers = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return response.json();
  },

  async get(path) {
    const response = await fetch(`${API_BASE_URL}${path}`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    return response.json();
  }
};
