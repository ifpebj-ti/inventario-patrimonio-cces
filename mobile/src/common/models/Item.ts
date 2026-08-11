import { Observation } from "./Observation";

export interface Item {
    id: number;
    code: string;
    description: string;
    price: number;
    responsible: string;
    locale?: string;
    isValid?: boolean;
    observations?: Observation[];
}