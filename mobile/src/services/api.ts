import axios from "axios";
import * as SecureStore from 'expo-secure-store';
import { router } from 'expo-router';

export const api = axios.create({
    baseURL: "http://10.0.2.2:8080",
    headers: {
        "Content-Type": "application/json"
    }
})

api.interceptors.request.use(
    async (config) => {
      const token = await SecureStore.getItemAsync('user_token');

      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
  
      return config;
    },
    (error) => {
      return Promise.reject(error);
    }
);

api.interceptors.response.use(
    (response) => {
      return response;
    },
    async (error) => {

      if (error.response?.status === 401) {

        await SecureStore.deleteItemAsync('user_token');
        
        router.replace('/(auth)/login');
      }
      
      return Promise.reject(error);
    }
);