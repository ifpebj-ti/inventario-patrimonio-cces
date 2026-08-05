import { User } from '@/common/models/User';
import { SignInRequestData } from '@/services/auth/types';
import { ReactNode } from 'react';

export interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  loading: boolean;
  signIn: (data: SignInRequestData) => Promise<void>;
  signOut: () => Promise<void>;
}

export interface ProviderProps {
  children: ReactNode;
}
