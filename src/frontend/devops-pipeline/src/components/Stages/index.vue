<template>
    <draggable v-model="computedStage" v-bind="dragOptions" :move="checkMove" class="devops-stage-list">
        <Stage v-for="(stage, index) in computedStage"
            :key="stage.id"
            class="list-item"
            :editable="editable"
            :stage="stage"
            :is-preview="isPreview"
            :can-skip-element="canSkipElement"
            :stage-index="index"
            :stage-length="computedStage.length"
            :containers="stage.containers">
        </Stage>
    </draggable>
</template>

<script>
    import { mapActions } from 'vuex'
    import Stage from './Stage'
    export default {
        components: {
            Stage
        },
        props: {
            editable: {
                type: Boolean,
                default: true
            },
            isPreview: {
                type: Boolean,
                default: false
            },
            canSkipElement: {
                type: Boolean,
                default: false
            },
            stages: {
                type: Array,
                default: []
            }
        },
        computed: {
            computedStage: {
                get () {
                    return this.stages
                },
                set (stages) {
                    const data = stages.map((stage, index) => {
                        const id = `stage-${index + 1}`
                        if (!stage.containers) { // container
                            return {
                                id,
                                name: id,
                                containers: [stage]
                            }
                        }

                        return {
                            id,
                            ...stage
                        }
                    })
                    this.setPipelineStage(data)
                    this.setPipelineEditing(true)
                }
            },
            dragOptions () {
                return {
                    group: 'pipeline-job',
                    ghostClass: 'sortable-ghost-atom',
                    chosenClass: 'sortable-chosen-atom',
                    animation: 130,
                    disabled: !this.editable
                }
            }
        },
        beforeDestroy () {
            window.showLinuxTipYet = false
        },
        methods: {
            ...mapActions('atom', ['setPipelineStage', 'setPipelineEditing']),
            checkMove (event) {
                const dragContext = event.draggedContext || {}
                const element = dragContext.element || {}
                const isTrigger = element.containers[0]['@type'] === 'trigger'

                const relatedContext = event.relatedContext || {}
                const relatedelement = relatedContext.element || {}
                const isRelatedTrigger = relatedelement['@type'] === 'trigger'
                const isTriggerStage = relatedelement.containers && relatedelement.containers[0]['@type'] === 'trigger'

                return !isTrigger && !isRelatedTrigger && !isTriggerStage
            }
        }
    }
</script>

<style lang="scss">
    .devops-stage-list {
        display: flex;
        padding-right: 120px;
        width: fit-content;
        position: relative;
        align-items: flex-start;
    }

    .list-item {
        transition: transform .2s ease-out;
    }

    .list-enter, .list-leave-to
        /* .list-complete-leave-active for below version 2.1.8 */ {
        opacity: 0;
        transform: translateY(36px) scale(0, 1);
    }

    .list-leave-active {
        position: absolute !important;
    }

</style>
