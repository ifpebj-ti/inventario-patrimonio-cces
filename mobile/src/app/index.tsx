import { View, Image } from "react-native";
import { Text } from "@/components/ui/text";
import icon from "@/assets/scaning-kid.png";
import { Button } from "@/components/ui/button";
import { router } from "expo-router";


export default function HomeScreen() {
    return (
        <View className="bg-white h-screen w-screen flex flex-col justify-center items-center">
            <Text className="text-blue-400 font-emphasis text-6xl text-center px-4 w-5/6">
                Inventarium
            </Text>
            <Text className="text-black font-normal text-lg text-center px-4 w-5/6 leading-tight">
                Realize seu login para gerenciar o inventário de sua instituição.
            </Text>
            <Image source={icon} className="w-2/3 h-1/2" resizeMode="contain"/>
            <View className="w-full flex flex-col justify-center items-center gap-4">
                <Button onPress={() => router.push("/(auth)/login")}>
                    <Text>Realizar Login</Text>
                </Button>
            </View>
        </View>
    )
} 