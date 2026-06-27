#include <stdio.h>

int main(void) {
    printf("Prime numbers between 1 and 100:\n");

    for (int n = 2; n <= 100; n++) {
        int is_prime = 1;

        /* Check divisibility up to sqrt(n): if i*i > n, no factor remains */
        for (int i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                is_prime = 0;
                break;
            }
        }

        if (is_prime) {
            printf("%d ", n);
        }
    }

    printf("\n");
    return 0;
}
