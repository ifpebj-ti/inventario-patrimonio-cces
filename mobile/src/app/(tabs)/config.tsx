import { Pressable, View } from "react-native";
import { Text } from "@/components/ui/text";
import { useAuth } from "@/hooks/useAuth";
import FontAwesome from "@expo/vector-icons/FontAwesome";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function HomeScreen() {
    const { user, signOut } = useAuth()

    return (
        <View className="min-h-screen flex flex-col justify-start items-center gap-8">
            <View className="flex flex-col justify-center items-start w-5/6">
                <View className="flex flex-row gap-6 mt-16">
                    <Text className="font-semibold">{user ? user.email : "teste"}</Text>
                    <Pressable onPress={() => signOut()}>
                        <FontAwesome name="sign-out" size={24} color="#60a5fa" />
                    </Pressable>
                </View>
                <Text className="font-semibold mb-8">{user ? user.name : "teste"}</Text>
                <Card>
                    <CardHeader>
                        <CardTitle>Sobre nós</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <Text className="text-justify">
                            Essa aplicação foi desenvolvida por estudantes do quinto período de Engenharia de Software e visa solucionar os problemas com relação a validação dos patrimônios vinculados ao curso.
                        </Text>
                    </CardContent>
                </Card>
            </View>
        </View>
    )
}