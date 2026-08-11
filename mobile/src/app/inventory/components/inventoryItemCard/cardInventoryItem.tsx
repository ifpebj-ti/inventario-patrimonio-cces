import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Text } from "@/components/ui/text";
import { Pressable } from "react-native";
import { InventoryCardItemProps } from "./types";

export const InventoryItemCard = ({
    code,
    description,
    responsible,
    price,
    locale,
    isValid,
    onPress
}: InventoryCardItemProps) => {
    return (
        <Pressable onPress={onPress}>
            <Card className="w-full">
                <CardHeader>
                    {
                        isValid ?
                        <CardTitle className="w-2/3 line-clamp-1 text-green-400">{code}</CardTitle> :
                        <CardTitle className="w-2/3 line-clamp-1 text-red-400">{code}</CardTitle> 
                    }
                    <CardDescription className="w-1/3">Ver detalhes</CardDescription>
                </CardHeader>
                <CardContent>
                    <Text className="line-clamp-2">{description}</Text>
                </CardContent>
            </Card>
        </Pressable>
    )
}