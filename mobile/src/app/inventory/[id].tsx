import { FlatList, Pressable, View } from "react-native";
import { Text } from "@/components/ui/text";
import { router, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Item } from "@/common/models/Item";
import { getInventoryItemsRequest } from "@/services/inventory/inventory";
import { InventoryItemCard } from "./components/inventoryItemCard/cardInventoryItem";

import FontAwesome6 from "@expo/vector-icons/FontAwesome6";

export default function InventoryDetails() {
    const { id } = useLocalSearchParams();
    const [items, setItems] = useState<Item[]>([]);

    const fetchItemsByInventory = useCallback(async () => {
        try {
          const inventoryId = Number(id);
          const page = 0;
          const pageSize = 2000;
    
          setItems([])
          const response = await getInventoryItemsRequest(
            inventoryId,
            page,
            pageSize,
          )
          setItems(response)
        } catch (error) {
          console.error('Erro ao carregar itens do inventário:', error)
        }
      }, [id])

      useEffect(() => {
        fetchItemsByInventory()
      }, [fetchItemsByInventory])
    

    return (
        <View className="flex flex-col justify-center items-center">
            <View className="w-5/6 mt-20">
                <View className="flex flex-row justify-evenly items-center p-4">
                    <Pressable>
                        <View
                            className="bg-card border-border flex flex-col gap-4 rounded-xl border p-6 shadow-sm shadow-black/5"
                        >
                            <FontAwesome6 name="qrcode" size={64} color="#60a5fa" />
                        </View>
                    </Pressable>
                    <Pressable>
                        <View
                            className="bg-card border-border flex flex-col gap-4 rounded-xl border p-6 shadow-sm shadow-black/5"
                        >
                            <FontAwesome6 name="barcode" size={64} color="#60a5fa" />
                        </View>
                    </Pressable>
                </View>
                <FlatList
                    data={items}
                    keyExtractor={(item) => item.id.toString()}
                    className="w-full h-screen"
                    contentContainerStyle={{ gap: 16 }}
                    renderItem={({ item }) => (
                        <InventoryItemCard
                            code={Number(item.code)}
                            description={item.description}
                            responsible={item.responsible}
                            price={item.price}
                            locale={item.locale ? item.locale : ""}
                            isValid={item.isValid ? item.isValid : false}
                            onPress={() => console.log(`Pressionando item com id: ${item.id}`)}
                        />
                    )}
                />
            </View>
        </View>
    )
}