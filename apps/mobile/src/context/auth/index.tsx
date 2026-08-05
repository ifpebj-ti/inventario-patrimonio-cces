import { User } from '@/common/models/User';
import { recoverUserInformation, signInRequest } from '@/services/auth/auth';
import { SignInRequestData } from '@/services/auth/types';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';

import { api } from '@/services/api';
import * as SecureStore from 'expo-secure-store';
import { AuthContext } from './context';
import { ProviderProps } from './types';

export const AuthProvider = ({ children }: ProviderProps) => {
  const [user, setUser] = useState<User | null>(null);

  const [loading, setLoading] = useState(false);

  const isAuthenticated = !!user;

  const signIn = async ({ email, password }: SignInRequestData): Promise<void> => {
    setLoading(true);
    try {
      const { token, user } = await signInRequest({
        email,
        password,
      });

      api.defaults.headers.common['Authorization'] = `Bearer ${token}`;

      await SecureStore.setItemAsync('user_token', token);

      setUser(user);
      router.replace('/(tabs)/home');
    } catch (error: any) {
      throw new Error('Erro ao logar no sistema do Inventarium', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    async function loadUserFromStorage() {
        const token = await SecureStore.getItemAsync("user_token");

        if (token) {
            try {
                api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                await recoverUserInformation().then((response) => {
                  setUser(response.data);
                });
                
                setUser(user);

            } catch (error) {
                console.error("Token inválido, deslogando:", error);
                await SecureStore.deleteItemAsync("user_token");
                delete api.defaults.headers.common['Authorization'];
                setUser(null);
            } finally {
                setLoading(false);
            }
        } else {
            setLoading(false);
        }
    }

    loadUserFromStorage();
  }, []);

  const signOut = async () => {
    router.push('/');
    await SecureStore.deleteItemAsync("user_token");
    setUser(null);
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        loading,
        signIn,
        signOut,
      }}>
      {children}
    </AuthContext.Provider>
  );
};
