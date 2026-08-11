import React from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { PortalHost } from '@rn-primitives/portal';
import { ThemeProvider } from '@react-navigation/native';
import { useColorScheme } from 'nativewind';
import { NAV_THEME } from '../lib/theme';

import "@/../global.css";
import { useFonts } from 'expo-font';
import { AuthProvider } from '@/context/auth';

export default function RootLayout() {
    const { colorScheme } = useColorScheme();
    const [fontsLoaded] = useFonts({
        'Montserrat-Regular': require('../assets/fonts/Montserrat-Regular.ttf'),
        'Montserrat-Bold': require('../assets/fonts/Montserrat-Bold.ttf'),
        'Montserrat-SemiBold': require('../assets/fonts/Montserrat-SemiBold.ttf'),
        'LindenHill-Regular': require('../assets/fonts/LindenHill-Regular.ttf'),
    });

    if (!fontsLoaded || !colorScheme) {
        return null;
    }
    
    if (!colorScheme) {
        return null;
    }

    return (
        <AuthProvider>
            <ThemeProvider value={NAV_THEME[colorScheme]}>
                <StatusBar style={colorScheme === 'dark' ? 'light' : 'dark'} />
                <Stack>
                    <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
                    <Stack.Screen name="(auth)" options={{ headerShown: false }} />
                    <Stack.Screen name="index" options={{ headerShown: false }} />
                    <Stack.Screen name="inventory/[id]" options={{ title: "Detalhes do Inventário" }} />
                </Stack>
                <PortalHost />
            </ThemeProvider>
        </AuthProvider>
    );
}