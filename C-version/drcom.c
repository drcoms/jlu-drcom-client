/*
 *  simple JLU drcom client 
 *  dirty hack version
 *
 */

/*
 * -- doc --
 * 数据包类型表示
 * 0x01 challenge request
 * 0x02 challenge response
 * 0x03 login request
 * 0x04 login response
 * 0x07 keep_alive request
 *		keep_alive response
 *		logout request
 *		logout response
 *		change_pass request
 *		change_pass response
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>

#include "md5.h"

// 必须修改，帐号密码和 mac 地址是绑定的
char user[] = "change to your account name here";
char pass[] = "change to your password here";
uint64_t mac = 0x000000000000; // echo 0x`ifconfig eth | egrep -io "([0-9a-f]{2}:){5}[0-9a-f]{2}" | tr -d ":"`


// 不一定要修改
char host[] = "drcom";
char os[] = "drcom";
int user_len = sizeof(user) - 1;
int pass_len = sizeof(pass) - 1;
int host_len = sizeof(host) - 1;
int os_len = sizeof(os) - 1;

// TODO 增加从文件读取参数

//SERVER_DOMAIN login.jlu.edu.cn
#define SERVER_ADDR "10.100.61.3"
#define SERVER_PORT 61440

#define RECV_DATA_SIZE 1000
#define SEND_DATA_SIZE 1000
#define CHALLENGE_TRY 10
#define LOGIN_TRY 5
#define ALIVE_TRY 5

/* infomation */
struct user_info_pkt {
	char *username;
	char *password;
	char *hostname;
	char *os_name;
	uint64_t mac_addr;
	int username_len;
	int password_len;
	int hostname_len;
	int os_name_len;
};

/* signal process flag */
int logout_flag = 0;

void get_user_info(struct user_info_pkt *user_info)
{
	user_info->username = user;
	user_info->username_len = user_len;
	user_info->password = pass;
	user_info->password_len = pass_len;
	user_info->hostname = host;
	user_info->hostname_len = host_len;
	user_info->os_name = os;
	user_info->os_name_len = os_len;
	user_info->mac_addr = mac;
}

void set_challenge_data(unsigned char *clg_data, int clg_data_len, int clg_try_count)
{
	/* set challenge */
	int random = rand() % 0xF0 + 0xF;
	int data_index = 0;
	memset(clg_data, 0x00, clg_data_len);
	/* 0x01 challenge request */
	clg_data[data_index++] = 0x01;
	/* clg_try_count first 0x02, then increment */
	clg_data[data_index++] = 0x02 + (unsigned char)clg_try_count;
	/* two byte of challenge_data */
	clg_data[data_index++] = (unsigned char)(random % 0xFFFF);
	clg_data[data_index++] = (unsigned char)((random % 0xFFFF) >> 8);
	/* end with 0x09 */
	clg_data[data_index++] = 0x09;
}

void challenge(int sock, struct sockaddr_in serv_addr, unsigned char *clg_data, int clg_data_len, char *recv_data, int recv_len)
{
	int ret;
	int challenge_try = 0;
	do {
		if (challenge_try > CHALLENGE_TRY) {
			close(sock);
			fprintf(stderr, "[drcom-challenge]: try challenge, but failed, please check your network connection.\n");
			exit(EXIT_FAILURE);
		}
		set_challenge_data(clg_data, clg_data_len, challenge_try);
		challenge_try++;
		ret = sendto(sock, clg_data, clg_data_len, 0, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
		if (ret != clg_data_len) {
			fprintf(stderr, "[drcom-challenge]: send challenge data failed.\n");
			continue;
		}
		ret = recvfrom(sock, recv_data, recv_len, 0, NULL, NULL);
		if (ret < 0) {
			fprintf(stderr, "[drcom-challenge]: recieve data from server failed.\n");
			continue;
		}
		if (*recv_data != 0x02) {
			if (*recv_data == 0x07) {
				close(sock);
				fprintf(stderr, "[drcom-challenge]: wrong challenge data.\n");
				exit(EXIT_FAILURE);
			}
			fprintf(stderr, "[drcom-challenge]: challenge failed!, try again.\n");
		}
	} while ((*recv_data != 0x02));
	fprintf(stdout, "[drcom-challenge]: challenge success!\n");
}

void set_login_data(struct user_info_pkt *user_info, unsigned char *login_data, int login_data_len, unsigned char *salt, int salt_len)
{
	/* login data */
	int i, j;
	unsigned char md5_str[16];
	unsigned char md5_str_tmp[100];
	int md5_str_len;

	int data_index = 0;

	memset(login_data, 0x00, login_data_len);

	/* magic 3 byte, username_len 1 byte */
	login_data[data_index++] = 0x03;
	login_data[data_index++] = 0x01;
	login_data[data_index++] = 0x00;
	login_data[data_index++] = (unsigned char)(user_info->username_len + 20);

	/* md5 0x03 0x01 salt password */
	md5_str_len = 2 + salt_len + user_info->password_len;
	memset(md5_str_tmp, 0x00, md5_str_len);
	md5_str_tmp[0] = 0x03;
	md5_str_tmp[1] = 0x01;
	memcpy(md5_str_tmp +2, salt, salt_len);
	memcpy(md5_str_tmp + 2 + salt_len, user_info->password, user_info->password_len);
	MD5(md5_str_tmp, md5_str_len, md5_str);
	memcpy(login_data + data_index, md5_str, 16);
	data_index += 16;

	/* user name 36 */
	memcpy(login_data + data_index, user_info->username, user_info->username_len);
	data_index += user_info->username_len > 36 ? user_info->username_len : 36;

	/* 0x00 0x00 */
	data_index += 2;

	/* (data[4:10].encode('hex'),16)^mac */
	uint64_t sum = 0;
	for (i = 0; i < 6; i++) {
		sum = (int)md5_str[i] + sum * 256;
	}
	sum ^= user_info->mac_addr;
	for (i = 6; i > 0; i--) {
		login_data[data_index + i - 1] = (unsigned char)(sum % 256);
		sum /= 256;
	}
	data_index += 6;
		
	/* md5 0x01 pwd salt 0x00 0x00 0x00 0x00 */
	md5_str_len = 1 + user_info->password_len + salt_len + 4;
	memset(md5_str_tmp, 0x00, md5_str_len);
	md5_str_tmp[0] = 0x01;
	memcpy(md5_str_tmp + 1, user_info->password, user_info->password_len);
	memcpy(md5_str_tmp + 1 + user_info->password_len, salt, salt_len);
	MD5(md5_str_tmp, md5_str_len, md5_str);
	memcpy(login_data + data_index, md5_str, 16);
	data_index += 16;

	/* 0x01 0x31 0x8c 0x21 0x28 0x00*12 */
	login_data[data_index++] = 0x01;
	login_data[data_index++] = 0x31;
	login_data[data_index++] = 0x8c;
	login_data[data_index++] = 0x21;
	login_data[data_index++] = 0x28;
	data_index += 12;

	/* md5 login_data[0-data_index] 0x14 0x00 0x07 0x0b 8 bytes */
	md5_str_len = data_index + 4;
	memset(md5_str_tmp, 0x00, md5_str_len);
	memcpy(md5_str_tmp, login_data, data_index);
	md5_str_tmp[data_index+0] = 0x14;
	md5_str_tmp[data_index+1] = 0x00;
	md5_str_tmp[data_index+2] = 0x07;
	md5_str_tmp[data_index+3] = 0x0b;
	MD5(md5_str_tmp, md5_str_len, md5_str);
	memcpy(login_data + data_index, md5_str, 8);
	data_index += 8;

	/* 0x01 0x00*4 */
	login_data[data_index++] = 0x01;
	data_index += 4;

	/* hostname */
	i = user_info->hostname_len > 71 ? 71 : user_info->hostname_len;
	memcpy(login_data + data_index, user_info->hostname, i);
	data_index += 71;

	/* 0x01 */
	login_data[data_index++] = 0x01;

	/* osname */
	i = user_info->os_name_len > 128 ? 128 : user_info->os_name_len;
	memcpy(login_data + data_index, user_info->os_name, i);
	data_index += 128;

	/* 0x6d 0x00 0x00 len(pass) */
	login_data[data_index++] = 0x6d;
	login_data[data_index++] = 0x00;
	login_data[data_index++] = 0x00;
	login_data[data_index++] = (unsigned char)(user_info->password_len);

	/* ror (md5 0x03 0x01 salt pass) pass */
	md5_str_len = 2 + salt_len + user_info->password_len;
	memset(md5_str_tmp, 0x00, md5_str_len);
	md5_str_tmp[0] = 0x03;
	md5_str_tmp[1] = 0x01;
	memcpy(md5_str_tmp +2, salt, salt_len);
	memcpy(md5_str_tmp + 2 + salt_len, user_info->password, user_info->password_len);
	MD5(md5_str_tmp, md5_str_len, md5_str);
	int ror_check = 0;
	for (i = 0; i < user_info->password_len; i++) {
		ror_check = (int)md5_str[i] ^ (int)(user_info->password[i]);
		login_data[data_index++] = (unsigned char)(((ror_check << 3) & 0xFF) + (ror_check >> 5));
	}

	/* 0x02 0x0c */
	login_data[data_index++] = 0x02;
	login_data[data_index++] = 0x0c;

	/* checksum point */
	int check_point = data_index;
	login_data[data_index++] = 0x01;
	login_data[data_index++] = 0x26;
	login_data[data_index++] = 0x07;
	login_data[data_index++] = 0x11;

	/* 0x00 0x00 mac */
	login_data[data_index++] = 0x00;
	login_data[data_index++] = 0x00;
	uint64_t mac = user_info->mac_addr;
	for (i = 0; i < 6; i++) {
		login_data[data_index + i - 1] = (unsigned char)(mac % 256);
		mac /= 256;
	}
	data_index += 6;

	/* 0x00 0x00 0x00 0x00 the last two byte I dont't know*/
	login_data[data_index++] = 0x00;
	login_data[data_index++] = 0x00;
	login_data[data_index++] = 0x00;
	login_data[data_index++] = 0x00;

	/* checksum */
	sum = 1234;
	uint64_t check = 0;
	for (i = 0; i < data_index; i += 4) {
		check = 0;
		for (j = 0; j < 4; j++) {
			check = check * 256 + (int)login_data[i+j];
		}
		sum ^= check;
	}
	sum = (1968 * sum) & 0xFFFFFFFF;
	for (j = 0; j < 4; j++) {
		login_data[check_point+j] = (unsigned char)(sum >> (j*8) & 0x000000FF);
	}
}

void login(int sock, struct sockaddr_in serv_addr, unsigned char *login_data, int login_data_len, char *recv_data, int recv_len)
{
	/* login */
	int ret = 0;
	int login_try = 0;
	do {
		if (login_try > LOGIN_TRY) {
			close(sock);
			fprintf(stderr, "[drcom-login]: try login, but failed, something wrong.\n");
			exit(EXIT_FAILURE);
		}
		login_try++;
		ret = sendto(sock, login_data, login_data_len, 0, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
		if (ret != login_data_len) {
			fprintf(stderr, "[drcom-login]: send login data failed.\n");
			continue;
		}
		ret = recvfrom(sock, recv_data, recv_len, 0, NULL, NULL);
		if (ret < 0) {
			fprintf(stderr, "[drcom-login]: recieve data from server failed.\n");
			continue;
		}
		if (*recv_data != 0x04) {
			if (*recv_data == 0x05) {
				close(sock);
				fprintf(stderr, "[drcom-login]: wrong password or username!\n\n");
				exit(EXIT_FAILURE);
			}
			fprintf(stderr, "[drcom-login]: login failed!, try again\n");
		}
	} while ((*recv_data != 0x04));
	fprintf(stdout, "[drcom-login]: login success!\n");
}

void set_alive_data(unsigned char *alive_data, int alive_data_len, unsigned char *tail, int tail_len, int alive_count, int random)
{
	// 0: 84 | 1: 82 | 2: 82 
	int i = 0;
	memset(alive_data, 0x00, alive_data_len);
	alive_data[i++] = 0x07;
	alive_data[i++] = (unsigned char)alive_count;
	alive_data[i++] = 0x28;
	alive_data[i++] = 0x00;
	alive_data[i++] = 0x0b;
	alive_data[i++] = (unsigned char)(alive_count * 2 + 1);
//	if (alive_count) {
		alive_data[i++] = 0xdc;
		alive_data[i++] = 0x02;
//	} else {
//		alive_data[i++] = 0x0f;
//		alive_data[i++] = 0x27;
//	}
	random += rand() % 10;
	for (i = 9 ; i > 7; i--) {
		alive_data[i] = random % 256; 
		random /= 256;
	}
	memcpy(alive_data+16, tail, tail_len);
	i = 25;
//	if (alive_count && alive_count % 3 == 0) {
//		/* crc */
//		memset(alive_data, 0xFF, 16);
//	}
}

void set_logout_data(unsigned char *logout_data, int logout_data_len)
{
	memset(logout_data, 0x00, logout_data_len);
	// TODO
	
}

void logout(int sock, struct sockaddr_in serv_addr, unsigned char *logout_data, int logout_data_len, char *recv_data, int recv_len)
{
	set_logout_data(logout_data, logout_data_len);
	// TODO
	
	close(sock);
	exit(EXIT_SUCCESS);
}

void logout_signal(int signum)
{
	logout_flag = 1;
}

int main(int argc, char **argv)
{
	int sock, ret;
	unsigned char send_data[SEND_DATA_SIZE];
	char recv_data[RECV_DATA_SIZE];
	struct sockaddr_in serv_addr;
	struct user_info_pkt user_info;

	sock = socket(AF_INET, SOCK_DGRAM, 0);
	if (sock < 0) {
		fprintf(stderr, "[drcom]: create sock failed.\n");
		exit(EXIT_FAILURE);
	}
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_addr.s_addr = inet_addr(SERVER_ADDR);
	serv_addr.sin_port = htons(SERVER_PORT);

	// get user information
	get_user_info(&user_info);

	// challenge data length 20
	challenge(sock, serv_addr, send_data, 20, recv_data, RECV_DATA_SIZE);

	// login data length 338, salt length 4
	set_login_data(&user_info, send_data, 338, (unsigned char *)(recv_data + 4), 4);
	memset(recv_data, 0x00, RECV_DATA_SIZE);
	login(sock, serv_addr, send_data, 338, recv_data, RECV_DATA_SIZE);

	// daemon process
	switch (fork()) {
		case -1:
			close(sock);
			fprintf(stderr, "[drcom-keep-alive]: drcom failed to run in daemon.\n");
			exit(EXIT_FAILURE);
		case 0:
			break;
		default:
			fprintf(stdout, "[drcom-keep-alive]: drcom running in daemon!\n");
			exit(EXIT_SUCCESS);
	}
	if (setsid() < 0) {
		exit(EXIT_FAILURE);
	}
	umask(0);
	if (chdir("/tmp/") < 0) {
		exit(EXIT_FAILURE);
	}
	close(STDIN_FILENO);
	close(STDOUT_FILENO);
	close(STDERR_FILENO);

	signal(SIGINT, logout_signal);
		
	// keep alive alive data length 42 or 40
	unsigned char tail[4];
	int tail_len = 4;
	memset(tail, 0x00, tail_len);
	int random = rand() % 0xFFFF;

	int alive_data_len = 0;
	int alive_count = 0;
	int alive_fail_count = 0;
	do { 
		if (alive_fail_count > ALIVE_TRY) {
			close(sock);
//			fprintf(stderr, "[drcom-keep-alive]: couldn't connect to network, check please.\n");
			exit(EXIT_FAILURE);
		}
		alive_data_len = alive_count > 0 ? 40 : 42;
		set_alive_data(send_data, alive_data_len, tail, tail_len, alive_count, random);
		ret = sendto(sock, send_data, alive_data_len, 0, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
		if (ret != alive_data_len) {
			alive_fail_count++;
//			fprintf(stderr, "[drcom-keep-alive]: send keep-alive data failed.\n");
			continue;
		} else {
			alive_fail_count = 0;
		}
		memset(recv_data, 0x00, RECV_DATA_SIZE);
		ret = recvfrom(sock, recv_data, RECV_DATA_SIZE, 0, NULL, NULL);
		if (ret < 0 || *recv_data != 0x07) {
			alive_fail_count++;
//			fprintf(stderr, "[drcom-keep-alive]: recieve keep-alive response data from server failed.\n");
			continue;
		} else {
			alive_fail_count = 0;
		}
		if (alive_count > 1) memcpy(tail, recv_data+16, tail_len);
		sleep(15);
//		fprintf(stdout, "[drcom-keep-alive]: keep alive.\n");
		alive_count = (alive_count + 1) % 3;
	} while (logout_flag != 1);

	// logout, data_length 80 or ?
	memset(recv_data, 0x00, RECV_DATA_SIZE);
	logout(sock, serv_addr, send_data, 80, recv_data, RECV_DATA_SIZE);

	return 0;
}
