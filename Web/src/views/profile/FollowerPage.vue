<template>
  <div style="max-width: 75%; margin: auto;">
    <div>
      <Profile />
      <UserStatus @state="setState" :follower_len="followers.length" :following_len="followings.length" />
      <v-row>
        <v-col cols="10">
          <v-combobox
            id="searchbox"
            v-model="select"
            :items="people"
            :filter="filterObject"
            solo
            outlined
            label="친구를 검색해 보세요"
          >
            <template v-slot:selection="data">
              <v-list-item-avatar>
                <img v-if="data.item.mem_image != 'default.img'" :src="data.item.mem_image">
                <img v-else src="https://www.pphfoundation.ca/wp-content/uploads/2018/05/default-avatar.png">
              </v-list-item-avatar>

              <v-list-item-title>
                <span style="font-weight: bold;">{{data.item.mem_name}}</span>
                {{data.item.mem_email}}
              </v-list-item-title>              
            </template>
            <template v-slot:item="data">
              <template>
                <v-list-item-avatar>
                  <img v-if="data.item.mem_image != 'default.img'" :src="data.item.mem_image">
                  <img v-else src="https://www.pphfoundation.ca/wp-content/uploads/2018/05/default-avatar.png">
                </v-list-item-avatar>

                <v-list-item-title>
                  <span style="font-weight: bold;">{{data.item.mem_name}}</span>
                  {{data.item.mem_email}}
                </v-list-item-title>
              </template>
            </template>

          </v-combobox>
        </v-col>
        <v-col cols="2" v-if="select && (select.mem_id != this.member.mem_id)">
          <div style="margin-top: 10px;">
            <v-btn v-if="includeCheck(select.mem_id)" @click="cancelFollow(select)">팔로우취소</v-btn>
            <v-btn v-else @click="follow(select)">팔로우</v-btn>
          </div>
        </v-col>
      </v-row>
      <v-row v-if="state == 1">
        <v-col>
          <div v-for="(follower, idx) in followers" :key="idx">
            <div style="display: flex; align-items: center; margin: 1rem;" class="followcard">
              <img style="width: 60px; height: 60px;" :src="follower.mem_image">
              <div style="text-align: left; margin: 0.5rem;">
                <div style="display: flex;">
                  <p style="margin: 0; font-weight: bold; font-size: 1.1rem;">{{follower.mem_name}}</p>
                  <a
                    v-if="!includeCheck(follower.mem_id)"
                    @click="follow(follower)"
                    style="color: skyblue; position: inline-block; margin: 0 2rem;"
                  >팔로우하기</a>
                </div>
                <p style="margin: 0;">{{follower.mem_email}}</p>
              </div>
            </div>
          </div>
        </v-col>
      </v-row>
      <v-row v-else-if="state == 0">
        <v-col>
          <div v-for="(following, idx) in followings" :key="idx">
            <div style="display: flex; justify-content: space-between; align-items: center; margin: 1rem;" class="followcard">
              <div style="display: flex; align-items: center;" >
                <img 
                  style="width: 60px; height: 60px;"
                  src="https://www.pphfoundation.ca/wp-content/uploads/2018/05/default-avatar.png"
                  v-if="following.mem_image == 'default.img'"
                >
                <img style="width: 60px; height: 60px;" v-else :src="following.mem_image">
                <div style="text-align: left; margin: 0.5rem;">
                  <p style="margin: 0; font-weight: bold; font-size: 1.1rem;">{{following.mem_name}}</p>
                  <p style="margin: 0;">{{following.mem_email}}</p>
                </div>
              </div>
              <div>
                <v-btn @click="cancelFollow(following)">팔로우취소</v-btn>
              </div>
            </div>
          </div>
        </v-col>
      </v-row>
      <!-- profile bar -->
    </div>

  </div>
</template>

<script>
import axios from 'axios'
import { mapState } from 'vuex'
import Profile from '../../components/profile/Profile.vue'
import UserStatus from '../../components/profile/UserStatus.vue'
export default {
  components: { Profile, UserStatus },
  name: 'FollowerPage',
  data() {
    return {
      people: [],
      state: 0,
      
      followers: [],
      followings: [],
      select: {},
    }
  },
  created() {
    axios.get(`https://localhost/api/follow/searchMember`, {
      params: {
        'searchWord': '',
      }
    })
      .then(res => {
        this.people = res.data.content.member
      })
    
    axios.get(`https://localhost/api/follow/getFollower`, {
      params: {
        'mem_id': this.member.mem_id
      }
    })
      .then((res) => {
        if (res.data.content.member) {
          this.followers = res.data.content.member
        }
      })
    
    axios.get(`https://localhost/api/follow/getFollowing`, {
      params: {
        'target_mem_id': this.member.mem_id
      }
    })
      .then((res) => {
        if (res.data.content.member) {
          this.followings = res.data.content.member
        }
      })
  },
  mounted() {
    this.select = null;
  },
  methods: {
    setState(data) {
      this.state = data
    },
    includeCheck(id) {
      if (id) {
        for (let i=0; i < this.followings.length; i++) {
          if (this.followings[i].mem_id == id) {
            return true
          }
        }
        return false
      }
    },
    follow(follower) {
      axios.post(`https://localhost/api/follow/insertFollowing`, {
        mem_id: this.member.mem_id,
        target_mem_id: follower.mem_id
      })
        .then(res => {
          if (res.data.message == 'SUCCESS') {
            axios.get(`https://localhost/api/member/getUser`, {
              params: {
                mem_id: follower.mem_id
              }
            })
              .then(data => {
                let info = data.data.content.member
                const new_member = {
                  'mem_id': info.mem_id,
                  'mem_name': info.mem_name,
                  'mem_email': info.mem_email,
                  'mem_image': info.mem_image,
                }
                this.followings.push(new_member)
              })
          }
        })
    },
    cancelFollow(following) {
      axios.delete(`https://localhost/api/follow/deleteFollowing`, {
        params: {
          mem_id: this.member.mem_id,
          target_mem_id: following.mem_id
        }
      })
        .then(res => {
          if (res.data.message == 'SUCCESS') {
            this.followings.splice(this.followings.indexOf(following), 1)
          }
        })
    },
    filterObject(item, queryText) {
      return (
        item.mem_name.toLocaleLowerCase().indexOf(queryText.toLocaleLowerCase()) > -1 ||
        item.mem_email.toLocaleLowerCase().indexOf(queryText.toLocaleLowerCase()) > -1
      );
    },
    clickObject() {
      console.log('click')
    }
  },
  computed: {
    ...mapState([
      'member'
    ])
  },
}
</script>

<style scoped>
  .followcard {
    border: 1px solid black !important;
    border-radius: 6px;
    padding: 1rem;
  }
</style>