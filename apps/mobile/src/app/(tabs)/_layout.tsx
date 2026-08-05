import { Tabs, router } from "expo-router";
import Entypo from "@expo/vector-icons/Entypo";
import FontAwesome6 from "@expo/vector-icons/FontAwesome6";
import { useAuth } from "@/hooks/useAuth";
import { useEffect } from "react";

export default function TabsLayout() {
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/")
    }
  }, [])

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#60a5fa',
        tabBarStyle: {
          backgroundColor: 'white',
          height: 80, 
        },
      }}
    >
      <Tabs.Screen
        name="home"
        options={{
          title: "Home",
          tabBarIcon: ({ color, size }) => (
            <Entypo name="home" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="config"
        options={{
          title: "Configurações",
          tabBarIcon: ({ color, size }) => (
            <FontAwesome6 name="gear" size={size} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}