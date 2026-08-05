import { z } from "zod";

export const loginFormSchema = z.object({
    email: z.string().email({ message: "Email inválido" }).min(1, {
        message: "Email obrigatório"
    }),
    password: z.string().min(8, {
        message: "A senha deve ter no mínimo 8 caracteres"
    })
})