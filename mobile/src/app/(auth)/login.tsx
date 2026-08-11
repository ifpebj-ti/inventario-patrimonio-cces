import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { View } from 'react-native';

import { loginFormSchema } from '@/common/validations/loginFormSchema';
import { useAuth } from '@/hooks/useAuth';
import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';
import z from 'zod';

type FormInput = z.infer<typeof loginFormSchema>;

export default function LoginScreen() {
  const { signIn } = useAuth();

  const {
    control,
    formState: { errors },
    handleSubmit,
  } = useForm<FormInput>({
    resolver: zodResolver(loginFormSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const handleSignIn = async (data: FormInput) => {
    await signIn(data);
  };

  return (
    <View className="min-w-screen flex min-h-screen flex-col items-center justify-center gap-4">
      <View className="mb-4 flex flex-row items-center">
        <Text className="w-5/6 px-4 text-center font-emphasis text-5xl text-blue-400">
          Inventarium
        </Text>
      </View>
      <View className="flex w-full flex-col items-center justify-center gap-4">
        <Controller
          control={control}
          name="email"
          render={({ field: { onChange, onBlur, value } }) => (
            <View className="flex w-4/5 flex-col gap-2">
              <Text>Email:</Text>
              <Input
                keyboardType="email-address"
                textContentType="emailAddress"
                autoComplete="email"
                placeholder="Digite seu email..."
                className="w-full"
                onChangeText={onChange}
                onBlur={onBlur}
                value={value}
              />
              {errors.email && <Text className="text-xs text-red-500">{errors.email.message}</Text>}
            </View>
          )}
        />
        <Controller
          control={control}
          name="password"
          render={({ field: { onChange, onBlur, value } }) => (
            <View className="flex w-4/5 flex-col gap-2">
              <Text>Senha:</Text>
              <Input
                keyboardType="default"
                secureTextEntry
                textContentType="password"
                placeholder="Digite sua senha..."
                onChangeText={onChange}
                onBlur={onBlur}
                value={value}
              />
              {errors.password && (
                <Text className="text-xs text-red-500">{errors.password.message}</Text>
              )}
            </View>
          )}
        />
        <Button className="mt-4 w-1/3" onPress={handleSubmit(handleSignIn)}>
          <Text>Realizar Login</Text>
        </Button>
      </View>
    </View>
  );
}
