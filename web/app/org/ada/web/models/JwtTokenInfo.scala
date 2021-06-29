package org.ada.web.models

import com.nimbusds.oauth2.sdk.token.{BearerAccessToken, RefreshToken}

import java.util.Date

case class JwtTokenInfo(accessToken: BearerAccessToken,
                        refreshToken: RefreshToken)
