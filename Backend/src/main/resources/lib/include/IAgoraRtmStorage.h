// Copyright (c) 2022 Agora.io. All rights reserved

// This program is confidential and proprietary to Agora.io.
// And may not be copied, reproduced, modified, disclosed to others, published
// or used, in whole or in part, without the express prior written permission
// of Agora.io.

#pragma once  // NOLINT(build/header_guard)

#include "AgoraRtmBase.h"

namespace agora {
namespace rtm {

/**
 * Metadata options.
 */
struct MetadataOptions {
  /**
  * Indicates whether or not to notify server update the modify timestamp of metadata
  */
  bool recordTs;
  /**
  * Indicates whether or not to notify server update the modify user id of metadata
  */
  bool recordUserId;

  MetadataOptions()
        : recordTs(false),
          recordUserId(false) {}
};

struct MetadataItem {
 public:
  /**
  * The key of the metadata item.
  */
  const char* key;
  /**
  * The value of the metadata item.
  */
  const char* value;
  /**
  * The User ID of the user who makes the latest update to the metadata item.
  */
  const char* authorUserId;
  /**
  * The revision of the metadata item.
  */
  int64_t revision;
  /**
  * The Timestamp when the metadata item was last updated.
  */
  int64_t updateTs;

  MetadataItem()
        : key(NULL),
          value(NULL),
          authorUserId(NULL),
          revision(-1),
          updateTs(0) {}

  MetadataItem(const char* key, const char* value, int64_t revision = -1)
        : key(key),
          value(value),
          authorUserId(NULL),
          revision(revision),
          updateTs(0) {}
};

class IMetadata {
 public:
  /**
   * Set the major revision of metadata.
   *
   * @param [in] revision The major revision of the metadata.
   */
  virtual void setMajorRevision(const int64_t revision) = 0;
  /**
   * Get the major revision of metadata.
   *
   * @return the major revision of metadata.
   */
  virtual int64_t getMajorRevision() const = 0;
  /**
   * Add or revise a metadataItem to current metadata.
   */
  virtual void setMetadataItem(const MetadataItem& item) = 0;
  /**
   * Get the metadataItem array of current metadata.
   *
   * @param [out] items The address of the metadataItem array.
   * @param [out] size The size the metadataItem array.
   */
  virtual void getMetadataItems(const MetadataItem** items, size_t* size) const = 0;
  /**
   * Clear the metadataItem array & reset major revision
   */
  virtual void clearMetadata() = 0;
  /**
   * Release the metadata instance.
   */
  virtual void release() = 0;
 protected:
  virtual ~IMetadata() {}
};

class IRtmStorage {
 public:
  /** Creates the metadata object and returns the pointer.
  * @return Pointer of the metadata object.
  */
  virtual IMetadata* createMetadata() = 0;

  /**
   * Set the metadata of a specified channel.
   *
   * @param [in] channelName The name of the channel.
   * @param [in] channelType Which channel type, RTM_CHANNEL_TYPE_STREAM or RTM_CHANNEL_TYPE_MESSAGE.
   * @param [in] data Metadata data.
   * @param [in] options The options of operate metadata.
   * @param [in] lock lock for operate channel metadata.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int setChannelMetadata(
      const char* channelName, RTM_CHANNEL_TYPE channelType, const IMetadata* data, const MetadataOptions& options, const char* lockName, uint64_t& requestId) = 0;
  /**
   * Update the metadata of a specified channel.
   *
   * @param [in] channelName The channel Name of the specified channel.
   * @param [in] channelType Which channel type, RTM_CHANNEL_TYPE_STREAM or RTM_CHANNEL_TYPE_MESSAGE.
   * @param [in] data Metadata data.
   * @param [in] options The options of operate metadata.
   * @param [in] lock lock for operate channel metadata.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int updateChannelMetadata(
      const char* channelName, RTM_CHANNEL_TYPE channelType, const IMetadata* data, const MetadataOptions& options, const char* lockName, uint64_t& requestId) = 0;
  /**
   * Remove the metadata of a specified channel.
   *
   * @param [in] channelName The channel Name of the specified channel.
   * @param [in] channelType Which channel type, RTM_CHANNEL_TYPE_STREAM or RTM_CHANNEL_TYPE_MESSAGE.
   * @param [in] data Metadata data.
   * @param [in] options The options of operate metadata.
   * @param [in] lock lock for operate channel metadata.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int removeChannelMetadata(
      const char* channelName, RTM_CHANNEL_TYPE channelType, const IMetadata* data, const MetadataOptions& options, const char* lockName, uint64_t& requestId) = 0;
  /**
   * Get the metadata of a specified channel.
   *
   * @param [in] channelName The channel Name of the specified channel.
   * @param [in] channelType Which channel type, RTM_CHANNEL_TYPE_STREAM or RTM_CHANNEL_TYPE_MESSAGE.
   * @param requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int getChannelMetadata(
      const char* channelName, RTM_CHANNEL_TYPE channelType, uint64_t& requestId) = 0;

  /**
   * Set the metadata of a specified user.
   *
   * @param [in] userId The user ID of the specified user.
   * @param [in] data Metadata data.
   * @param [in] options The options of operate metadata.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int setUserMetadata(
      const char* userId, const IMetadata* data, const MetadataOptions& options, uint64_t& requestId) = 0;
  /**
   * Update the metadata of a specified user.
   *
   * @param [in] userId The user ID of the specified user.
   * @param [in] data Metadata data.
   * @param [in] options The options of operate metadata.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int updateUserMetadata(
      const char* userId, const IMetadata* data, const MetadataOptions& options, uint64_t& requestId) = 0;
  /**
   * Remove the metadata of a specified user.
   *
   * @param [in] userId The user ID of the specified user.
   * @param [in] data Metadata data.
   * @param [in] options The options of operate metadata.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int removeUserMetadata(
      const char* userId, const IMetadata* data, const MetadataOptions& options, uint64_t& requestId) = 0;
  /**
   * Get the metadata of a specified user.
   *
   * @param [in] userId The user ID of the specified user.
   * @param [out] requestId The unique ID of this request.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int getUserMetadata(const char* userId, uint64_t& requestId) = 0;

  /**
   * Subscribe the metadata update event of a specified user.
   *
   * @param [in] userId The user ID of the specified user.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int subscribeUserMetadata(const char* userId, uint64_t& requestId) = 0;
  /**
   * unsubscribe the metadata update event of a specified user.
   *
   * @param [in] userId The user ID of the specified user.
   * 
   * @return
   * - 0: Success.
   * - < 0: Failure.
   */
  virtual int unsubscribeUserMetadata(const char* userId) = 0;

 protected:
  virtual ~IRtmStorage() {}
};

}  // namespace rtm
}  // namespace agora
