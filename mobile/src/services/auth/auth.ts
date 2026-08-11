import { AxiosError } from "axios"
import { api } from "../api"
import { SignInRequestData } from "./types"

export const signInRequest = async (data: SignInRequestData) => {
  try {
    const response = await api.post("/auth/login", data);
    return response.data;
  } catch (error) {
    const axiosError = error as AxiosError;
    const errorMessage = (axiosError.response?.data as string)
      ?.trim()
      .toLowerCase();
    if (errorMessage === 'email not verified') {
      throw new Error('Email não verificado');
    }
    throw new Error('Falha ao realizar o login');
  }
}

export const recoverUserInformation = async () => {
  const response = await api.get('/auth/me')
  return response
}