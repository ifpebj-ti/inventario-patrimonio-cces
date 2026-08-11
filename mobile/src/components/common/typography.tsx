import React from "react";
import { Text, TextProps } from "react-native";
import { cn } from "@/lib/utils";

const Title = React.forwardRef<Text, TextProps>(
  ({ className, children, ...props }, ref) => (
    <Text
      ref={ref}
      className={cn(
        "text-blue-400 font-emphasis text-5xl text-center",
        className 
      )}
      {...props}
    >
      {children}
    </Text>
  )
);
Title.displayName = "Title";

const Subtitle = React.forwardRef<Text, TextProps>(
  ({ className, children, ...props }, ref) => (
    <Text
      ref={ref}
      className={cn(
        "text-blue-400 dark:text-gray-300 font-normal text-lg text-center",
        className
      )}
      {...props}
    >
      {children}
    </Text>
  )
);
Subtitle.displayName = "Subtitle";

export { Title, Subtitle };