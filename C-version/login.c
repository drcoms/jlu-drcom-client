#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "md5.h"

/*
    Author: latyas@gmail.com
    Incomplete.
*/
extern void md5( const unsigned char *input, size_t ilen, unsigned char output[16] );
struct login_packet
{
    unsigned char magic[3];
    unsigned char username_len[1];
    unsigned char md5_1[16];
    unsigned char username[36];
    unsigned char delimiter_1[2];
    unsigned char encrypted_1[6];
    unsigned char md5_2[16];
    unsigned char delimiter_2[17]; /* '\x01\x31\x8c\x31\x4e' + '\00'*12 */
    unsigned char host_name[71];
    unsigned char delimiter_3[1]; /* \0x01*/
    unsigned char host_os[128];
    unsigned char delimiter_4[3]; /*'\x6d\x00\x00'*/
    unsigned char password_len[1];
    unsigned char ror_md5[16];
    unsigned char delimiter_4[2];
    unsigned char verifier[4];
    unsigned char delimiter_5[2];
    unsigned char mac[6];


};



struct login_packet *login_verifier(struct login_packet *pkt)
{
    return pkt;
}
struct login_packet *login_init(struct login_packet *pkt)
{
    memset(pkt, 0, sizeof(*pkt));
    strcpy(pkt->magic, "\x03\x01\x00");

    return pkt;
}
struct login_packet *login_sign(char *username, char *password, char* mac, char* salt, struct login_packet *pkt)
{
    unsigned char* md5_1[16];

    /* username length*/
    pkt->username_len[0] = strlen(username) + 20;
    /* calculate password md5*/
    _get_salted_password(password, salt, md5_1);
    memcpy(pkt->md5_1,md5_1,16);
    /* set mac*/
    memcpy(pkt->mac, mac, 6);
}
char *_get_salted_password(char *password, char* salt, char md5_buffer[16])
{
    unsigned int salt_len = strlen(salt);
    unsigned int password_len = strlen(password);
    char *buffer = malloc(2 + salt_len + password_len);
    strcpy(buffer, "\x03\x01");
    memcpy(buffer+2, salt, salt_len);
    memcpy(buffer+2+salt_len, password, password_len);

    md5(buffer, 2 + salt_len + password_len, md5_buffer)
    return md5_buffer;
}
