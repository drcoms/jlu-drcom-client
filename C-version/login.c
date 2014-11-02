#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "md5.h"

/*
    Author: latyas@gmail.com
    Incomplete.
*/
enum {
EUSR_TOO_LONG = 10,
EHOSTNAME_TOO_LONG
};

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
    unsigned char md5_3[16];
    unsigned char delimeter_2_1[5]; /* \x01\x00\x00\x00\x00*/
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
struct login_packet *login_sign(char *username, char *password, char* mac, char* hostname,  char* salt, struct login_packet *pkt)
{
    unsigned char* md5_buffer[16];

    /* username length*/
    pkt->username_len[0] = strlen(username) + 20;
    /* calculate password md5*/
    _get_salted_password_1(password, salt, md5_buffer);
    memcpy(pkt->md5_1,md5_1,16);

    /* username */
    if (strlen(username) > 36) exit(EUSR_TOO_LONG);
    memcpy(pkt->username, username, strlen(username));

    /* delimeter_1*/
    memset(pkt->delimiter_1,0,2);


    /*encrypted_1*/
    for(int i=0; i<6; i++)
    {
        pkt->encrypted_1[i] = pkt->md5_1[i] ^ mac[i];
    }

    /* salted password md5_2*/
    _get_salted_password_2(password, salt, md5_buffer);
    memcpy(pkt->md5_2,md5_buffer,16);

    /* delimeter 2 */
    memcpy(pkt->delimiter_2,"\x01\x31\x8c\x31\x4e\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00",17);

    /* md5_2 */

    /* hostname */
    if (strlen(hostname) > 71) exit(EHOSTNAME_TOO_LONG);

    /**/
    /*
    data += dump(int(data[4:10].encode('hex'),16)^mac).rjust(6,'\x00')
    first 6 bytes of md5_1
    */

    /* set mac*/
    memcpy(pkt->mac, mac, 6);
}
char *_get_salted_password_1(char *password, char* salt, char md5_buffer[16])
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
char *_get_salted_password_2(char *password, char* salt, char md5_buffer[16])
{
    unsigned int salt_len = strlen(salt);
    unsigned int password_len = strlen(password);
    char *buffer = malloc(5 + salt_len + password_len);

    strcpy(buffer, "\x01");
    memcpy(buffer+1, password, password_len);
    memcpy(buffer+1+password_len, salt, salt_len);
    memset(buffer+1+password_len+salt_len,0,4);

    md5(buffer, 5 + salt_len + password_len, md5_buffer)
    return md5_buffer;
}
int _ror(int value, int places)
{
  return (value>>places)|(value<<WORD_LENGTH-places);
}
