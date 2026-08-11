import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Text } from "@/components/ui/text";
import { CardHomeProps } from "./types";
import { Pressable } from "react-native";

export const HomeCard = ({ title, description, onPress }: CardHomeProps) => {
    return (
        <Pressable onPress={onPress}>
            <Card className="w-full">
                <CardHeader>
                    <CardTitle className="w-2/3 line-clamp-1">{title}</CardTitle>
                    <CardDescription className="w-1/3">Ver detalhes</CardDescription>
                </CardHeader>
                <CardContent>
                    <Text className="line-clamp-2">{description}</Text>
                </CardContent>
            </Card>
        </Pressable>
    )
}