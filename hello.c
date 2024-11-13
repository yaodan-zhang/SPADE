#include <stdio.h>

int main(int argc, char **argv) {
    if(argc == 1) {
        printf("Hello, world!\n");
    } else if(argc == 2) {
        printf("Hello, SPADE!\n");
    }
    return 0;
}