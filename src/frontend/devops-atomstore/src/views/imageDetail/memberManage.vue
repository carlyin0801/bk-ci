<template>
    <div class="member-manage-wrapper">
        <div class="inner-header">
            <div class="title"> {{ $t('store.成员管理') }} </div>
        </div>

        <section
            class="sub-view-port"
            v-bkloading="{
                isLoading: loading.isLoading,
                title: loading.title
            }">
            <div class="member-manage-content" v-if="showContent && memberList.length">
                <div class="info-header">
                    <button class="bk-button bk-primary add-button" type="button" @click="addMember" v-if="userInfo.isProjectAdmin"> {{ $t('store.新增成员') }} </button>
                    <div class="member-total"> {{ $t('store.该插件目前有') }} <span>{{ memberCount }}</span> {{ $t('store.名成员') }} </div>
                </div>
                <div class="member-content">
                    <bk-table style="margin-top: 15px;" :data="memberList">
                        <bk-table-column :label="$t('store.成员')" prop="userName"></bk-table-column>
                        <bk-table-column :label="$t('store.调试项目')" prop="projectName"></bk-table-column>
                        <bk-table-column :label="$t('store.角色')" prop="type" :formatter="typeFormatter"></bk-table-column>
                        <bk-table-column :label="$t('store.描述')" prop="type" :formatter="desFormatter"></bk-table-column>
                        <bk-table-column :label="$t('store.操作')" width="120" class-name="handler-btn">
                            <template slot-scope="props">
                                <span :class="[{ 'disable': !userInfo.isProjectAdmin } ,'update-btn']" @click="handleDelete(props.row)"> {{ $t('store.删除') }} </span>
                            </template>
                        </bk-table-column>
                    </bk-table>
                </div>
            </div>
            <empty-tips v-if="showContent && !memberList.length"
                :title="emptyTipsConfig.title"
                :desc="emptyTipsConfig.desc"
                :btns="emptyTipsConfig.btns">
            </empty-tips>
        </section>
        <add-member-dialog :show-dialog="showDialog"
            :project-code="currentImage.projectCode"
            :permission-list="permissionList"
            @confirmHandle="confirmHandle"
            @cancelHandle="cancelHandle">
        </add-member-dialog>
    </div>
</template>

<script>
    import { mapGetters } from 'vuex'
    import emptyTips from '@/components/img-empty-tips'
    import addMemberDialog from '@/components/add-member-dialog'

    export default {
        components: {
            emptyTips,
            addMemberDialog
        },
        data () {
            return {
                memberCount: 0,
                showContent: false,
                showDialog: false,
                memberList: [],
                memberType: {
                    'ADMIN': 'Owner',
                    'DEVELOPER': 'Developer'
                },
                permissionList: [
                    { name: this.$t(this.$t('store.镜像发布')), active: false, type: 'DEVELOPER' },
                    { name: this.$t('store.审批'), active: false, type: 'ADMIN' },
                    { name: this.$t('store.成员管理'), active: false, type: 'ADMIN' },
                    { name: this.$t('store.可见范围'), active: false, type: 'ADMIN' }
                ],
                loading: {
                    isLoading: false,
                    title: ''
                }
            }
        },
        computed: {
            ...mapGetters('store', {
                'currentImage': 'getCurrentImage',
                'userInfo': 'getImageMeminfo'
            }),

            imageCode () {
                return this.$route.params.imageCode
            },

            emptyTipsConfig () {
                return {
                    title: this.$t('store.暂时没有成员'),
                    desc: this.$t('store.可以新增镜像的管理人员或开发人员'),
                    btns: [
                        {
                            type: 'primary',
                            size: 'normal',
                            handler: () => this.addMember(),
                            text: this.$t('store.新增成员'),
                            disable: !this.userInfo.isProjectAdmin
                        }
                    ]
                }
            }
        },

        async mounted () {
            await this.init()
        },

        methods: {
            typeFormatter (row, column, cellValue, index) {
                return this.memberType[cellValue]
            },

            desFormatter (row, column, cellValue, index) {
                return cellValue === 'ADMIN' ? this.$t('store.镜像发布 审批 成员管理 可见范围') : this.$t('store.镜像发布')
            },

            async init () {
                this.loading.isLoading = true

                try {
                    await this.requestList()
                } catch (err) {
                    this.$bkMessage({
                        message: err.message ? err.message : err,
                        theme: 'error'
                    })
                } finally {
                    setTimeout(() => {
                        this.loading.isLoading = false
                        this.showContent = true
                    }, 100)
                }
            },
            async requestList () {
                try {
                    const res = await this.$store.dispatch('store/requestImageMemList', this.imageCode)
                    this.memberList.splice(0, this.memberList.length)
                    if (res) {
                        this.memberCount = res.length
                        res.map(item => {
                            this.memberList.push(item)
                        })
                    }
                } catch (err) {
                    this.memberCount = 0
                    this.memberList = []

                    const message = err.message ? err.message : err
                    const theme = 'error'
                    this.$bkMessage({
                        message,
                        theme
                    })
                }
            },

            addMember () {
                this.showDialog = true
            },

            async confirmHandle (params) {
                let message, theme
                try {
                    await this.$store.dispatch('store/requestAddImageMem', Object.assign(params, { storeCode: this.imageCode }))

                    message = this.$t('store.新增成功')
                    theme = 'success'
                    this.requestList()
                    this.cancelHandle()
                } catch (err) {
                    message = message = err.message ? err.message : err
                    theme = 'error'
                } finally {
                    this.$bkMessage({
                        message,
                        theme
                    })
                }
            },

            cancelHandle () {
                this.showDialog = false
            },

            async requestDeleteMember (id) {
                let message, theme
                try {
                    await this.$store.dispatch('store/requestDeleteImageMem', {
                        imageCode: this.imageCode,
                        id
                    })

                    message = this.$t('store.删除成功')
                    theme = 'success'
                    this.requestList()
                } catch (err) {
                    message = message = err.message ? err.message : err
                    theme = 'error'
                } finally {
                    this.$bkMessage({
                        message,
                        theme
                    })
                }
            },

            handleDelete (row) {
                if (!this.userInfo.isProjectAdmin) return
                const h = this.$createElement
                const subHeader = h('p', {
                    style: {
                        textAlign: 'center'
                    }
                }, `${this.$t('store.确定删除成员')}(${row.userName})？`)

                this.$bkInfo({
                    title: this.$t('store.删除'),
                    subHeader,
                    confirmFn: async () => {
                        this.requestDeleteMember(row.id)
                    }
                })
            }
        }
    }
</script>

<style lang="scss">
    @import './../../assets/scss/conf';
    .member-manage-wrapper {
        height: 100%;
        overflow: auto;
        .inner-header {
            display: flex;
            justify-content: space-between;
            padding: 18px 20px;
            width: 100%;
            height: 60px;
            border-bottom: 1px solid $borderWeightColor;
            background-color: #fff;
            box-shadow:0px 2px 5px 0px rgba(51,60,72,0.03);
            .title {
                font-size: 16px;
            }
        }
        .member-manage-content {
            height: 100%;
            padding: 20px;
            overflow: auto;
        }
        .info-header {
            display: flex;
            justify-content: space-between;
            .member-total {
                line-height: 36px;
                span {
                    margin: auto 4px;
                }
            }
        }
        .member-table {
            margin-top: 20px;
            border: 1px solid $borderWeightColor;
            tbody {
                background-color: #fff;
            }
            th {
                height: 42px;
                padding: 2px 10px;
                color: #333C48;
                font-weight: normal;
                &:first-child {
                    padding-left: 20px;
                }
            }
            td {
                height: 42px;
                padding: 2px 10px;
                &:first-child {
                    padding-left: 20px;
                }
                &:last-child {
                    padding-right: 30px;
                }
            }
            .handler-btn {
                span {
                    display: inline-block;
                    margin-right: 20px;
                    color: $primaryColor;
                    cursor: pointer;
                }
            }
        }
    }
</style>
