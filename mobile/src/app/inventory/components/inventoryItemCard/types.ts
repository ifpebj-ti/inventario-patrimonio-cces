export interface InventoryCardItemProps {
    code: number;
    description: string;
    responsible: string;
    price: number;
    locale: string;
    isValid: boolean;
    onPress: () => void;
}