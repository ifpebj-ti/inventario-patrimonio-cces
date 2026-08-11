import React from 'react';
import { View } from 'react-native';
import { Button } from '@/components/ui/button';
import { Text } from '@/components/ui/text';

import { Feather } from '@expo/vector-icons'; 
import { useColorScheme } from 'nativewind';

interface PaginationControlsProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (newPage: number) => void;
}

export const PaginationControls = ({
  currentPage,
  totalPages,
  onPageChange,
}: PaginationControlsProps) => {
  const { colorScheme } = useColorScheme();
  
  const iconColor = colorScheme === 'dark' ? '#FFFFFF' : '#000000';

  if (totalPages <= 1) {
    return null;
  }

  const isFirstPage = currentPage === 0;
  const isLastPage = currentPage + 1 >= totalPages;

  return (
    <View className="flex-row items-center justify-between w-full mt-4">
      <Button
        variant="outline"
        size="sm"
        disabled={isFirstPage}
        onPress={() => onPageChange(currentPage - 1)}
      >
        <View className="flex-row items-center">
          <Feather name="chevron-left" size={16} color={iconColor} />
          <Text className="ml-2">Anterior</Text>
        </View>
      </Button>

      <Text className="text-muted-foreground font-semibold">
        Página {currentPage + 1} de {totalPages}
      </Text>

      <Button
        variant="outline"
        size="sm"
        disabled={isLastPage}
        onPress={() => onPageChange(currentPage + 1)}
      >
        <View className="flex-row items-center">
          <Text className="mr-2">Próximo</Text>
          <Feather name="chevron-right" size={16} color={iconColor} />
        </View>
      </Button>
    </View>
  );
};