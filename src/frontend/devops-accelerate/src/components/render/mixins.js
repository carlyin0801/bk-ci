function isEmpty (val) {
    return [null, undefined, ''].includes(val)
}

export default {
    props: {
        paramEnum: Object,
        paramKey: String,
        disabled: Boolean,
        paramValue: Object,
        defaultValue: [String, Array, Object]
    },

    computed: {
        displayValue () {
            const value = this.paramValue[this.paramKey]
            const keys = Object.keys(this.paramEnum) || []
            const curKey = keys.find((key) => (+this.paramEnum[key] === +value)) || ''
            return curKey
        }
    },

    created () {
        this.handleDefaultValue()
    },

    methods: {
        handleDefaultValue () {
            const curValue = (this.paramValue)[this.paramKey]
            if (isEmpty(curValue)) this.changeParamValue(this.defaultValue)
        },

        changeParamValue (val) {
            this.$emit('value-change', this.paramKey, val)
        }
    }
}
