import { ActivityIndicator, Alert, FlatList, View } from "react-native";
import { Title } from "@/components/common/typography";
import { HomeCard } from "./components/homeCard";
import { useCallback, useEffect, useState } from "react";
import { InventoryResponse } from "@/services/inventory/types";
import { getUserInventoriesRequest } from "@/services/inventory/inventory";
import { router } from "expo-router";

export default function HomeScreen() {
    const [isLoading, setIsLoading] = useState(false);
    const [inventories, setInventories] = useState<InventoryResponse[]>([]);

    const fetchInventories = useCallback(async () => {
        setIsLoading(true);
        try {
          const response = await getUserInventoriesRequest();
          setInventories(response);
        } catch (error) {
          console.error('Erro ao carregar inventários:', error);

          Alert.alert(
            'Erro', 
            'Não foi possível carregar seus inventários.'
          );
        } finally {
          setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchInventories();
    }, [fetchInventories]);

    if (isLoading) {
        return (
            <View className="flex-1 justify-center items-center">
                <ActivityIndicator size="large" />
            </View>
        );
    }

    return (
        <View className="flex flex-col justify-start items-center gap-8">
            <Title className="mt-16">Inventarium</Title>
            <View className="w-5/6 flex flex-col gap-4">
                <FlatList
                    data={inventories}
                    keyExtractor={(item) => item.id.toString()}
                    className="w-full"
                    contentContainerStyle={{ gap: 16 }}
                    renderItem={({ item }) => (
                        <HomeCard
                            title={item.name}
                            description={item.description}
                            onPress={() => router.push(`inventory/${item.id}`)}
                        />
                    )}
                />
            </View>
        </View>
    )
}